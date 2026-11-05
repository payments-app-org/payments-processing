package com.paymentsapp.processing;

import com.paymentsapp.platform.redis.IdempotentResults;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Заглушка процессинга — физического движения денег (настоящий делает другая команда).
// Идемпотентен по paymentId: повторный запрос получает тот же результат, перевод «не проводится» дважды.
// Для ручной проверки: копейки .88 → DECLINED, .99 → ответ дольше таймаута payment (перевод при этом проведён).
// Итоги — в Redis с TTL: повтор после рестарта или на другом поде получает тот же ответ.
// Долгие переводы (за рубеж, пополнение из другого банка по СБП — там клиент подтверждает списание) отвечают
// IN_PROGRESS; итог через processing.async.delay-* отправит payment AsyncCompletion.
@RestController
@RequestMapping("/api/processing")
public class ProcessingController {

    private static final Logger log = LoggerFactory.getLogger(ProcessingController.class);
    private static final List<String> DECLINE_REASONS = List.of(
            "Банк получателя отклонил перевод", "Получатель не найден", "Технический сбой на стороне банка");

    private static final String EXTERNAL_PREFIX = "PRC-";
    private static final int KOPECKS = 2;

    private final IdempotentResults<TransferResponse> results;
    private final PendingTransfers pending;
    private final ProcessingProperties props;

    public ProcessingController(IdempotentResults<TransferResponse> transferResults, PendingTransfers pending,
                                ProcessingProperties props) {
        this.results = transferResults;
        this.pending = pending;
        this.props = props;
    }

    @PostMapping("/transfers")
    public TransferResponse transfer(@Valid @RequestBody TransferRequest req) throws InterruptedException {
        TransferResponse cached = results.find(req.paymentId()).orElse(null);
        if (cached != null) {
            log.info("payment {}: повтор, отдаём сохранённый результат {}", req.paymentId(), cached.status());
            return cached;
        }

        int kopecks = req.amount().remainder(BigDecimal.ONE).movePointRight(KOPECKS).intValue();
        double roll = ThreadLocalRandom.current().nextDouble();
        boolean slow = kopecks == props.slowKopecks() || (kopecks != props.declinedKopecks() && roll < props.timeoutRate());
        boolean declined = kopecks == props.declinedKopecks() || (!slow && roll < props.timeoutRate() + props.declineRate());

        TransferResponse result = declined
                ? new TransferResponse(req.paymentId(), Status.DECLINED, null,
                        DECLINE_REASONS.get(ThreadLocalRandom.current().nextInt(DECLINE_REASONS.size())))
                : new TransferResponse(req.paymentId(), Status.COMPLETED, EXTERNAL_PREFIX + UUID.randomUUID(), null);
        if (isAsync(req)) {
            // Принят: итог решён заранее, но payment узнает его позже — уведомлением или запросом статуса
            TransferResponse accepted = new TransferResponse(req.paymentId(), Status.IN_PROGRESS, null, null);
            TransferResponse first = results.putIfAbsent(req.paymentId(), accepted);
            if (first == accepted) {
                pending.schedule(result);
                log.info("payment {}: принят, итог — через {}–{}", req.paymentId(), props.async().delayMin(),
                        props.async().delayMax());
            }
            return first;
        }
        // Результат фиксируем до «долгого ответа»: перевод уже проведён, просто ответ потерялся по дороге
        TransferResponse first = results.putIfAbsent(req.paymentId(), result);
        if (first != result) {
            return first;
        }
        if (slow) {
            log.info("payment {}: {}, но отвечаем через {}", req.paymentId(), result.status(), props.slowDelay());
            Thread.sleep(props.slowDelay());
        }
        log.info("payment {}: {}", req.paymentId(), result.status());
        return result;
    }

    // Запрос статуса: payment спрашивает, если не дождался ответа на перевод
    @GetMapping("/transfers/{paymentId}")
    public ResponseEntity<TransferResponse> status(@PathVariable UUID paymentId) {
        return results.find(paymentId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private boolean isAsync(TransferRequest req) {
        return props.async().types().contains(req.type()) || (props.async().sbpPull() && req.sourceBankId() != null);
    }

    public enum Status { COMPLETED, DECLINED, IN_PROGRESS }

    // sourceBankId — банк, из которого забираем деньги по СБП (пополнение со своего счёта в другом банке)
    public record TransferRequest(@NotNull UUID paymentId, @NotNull String type, @NotNull BigDecimal amount,
                                  String sourceBankId, String recipientPhone, String recipientBankId,
                                  String recipientCardNumber) {
    }

    public record TransferResponse(UUID paymentId, Status status, String externalId, String reason) {
    }
}
