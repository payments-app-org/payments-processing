package com.paymentsapp.processing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
@RestController
@RequestMapping("/api/processing")
public class ProcessingController {

    private static final Logger log = LoggerFactory.getLogger(ProcessingController.class);
    private static final List<String> DECLINE_REASONS = List.of(
            "Банк получателя отклонил перевод", "Получатель не найден", "Технический сбой на стороне банка");

    private final Map<UUID, TransferResponse> results = new ConcurrentHashMap<>();
    private final double declineRate;
    private final double timeoutRate;
    private final Duration slowDelay;

    public ProcessingController(@Value("${processing.decline-rate}") double declineRate,
                                @Value("${processing.timeout-rate}") double timeoutRate,
                                @Value("${processing.slow-delay}") Duration slowDelay) {
        this.declineRate = declineRate;
        this.timeoutRate = timeoutRate;
        this.slowDelay = slowDelay;
    }

    @PostMapping("/transfers")
    public TransferResponse transfer(@Valid @RequestBody TransferRequest req) throws InterruptedException {
        TransferResponse cached = results.get(req.paymentId());
        if (cached != null) {
            log.info("payment {}: повтор, отдаём сохранённый результат {}", req.paymentId(), cached.status());
            return cached;
        }

        int kopecks = req.amount().remainder(BigDecimal.ONE).movePointRight(2).intValue();
        double roll = ThreadLocalRandom.current().nextDouble();
        boolean slow = kopecks == 99 || (kopecks != 88 && roll < timeoutRate);
        boolean declined = kopecks == 88 || (!slow && roll < timeoutRate + declineRate);

        TransferResponse result = declined
                ? new TransferResponse(req.paymentId(), Status.DECLINED, null,
                        DECLINE_REASONS.get(ThreadLocalRandom.current().nextInt(DECLINE_REASONS.size())))
                : new TransferResponse(req.paymentId(), Status.COMPLETED, "PRC-" + UUID.randomUUID(), null);
        // Результат фиксируем до «долгого ответа»: перевод уже проведён, просто ответ потерялся по дороге
        TransferResponse first = results.putIfAbsent(req.paymentId(), result);
        if (first != null) {
            return first;
        }
        if (slow) {
            log.info("payment {}: {}, но отвечаем через {}", req.paymentId(), result.status(), slowDelay);
            Thread.sleep(slowDelay);
        }
        log.info("payment {}: {}", req.paymentId(), result.status());
        return result;
    }

    // Запрос статуса: payment спрашивает, если не дождался ответа на перевод
    @GetMapping("/transfers/{paymentId}")
    public ResponseEntity<TransferResponse> status(@PathVariable UUID paymentId) {
        TransferResponse result = results.get(paymentId);
        return result == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(result);
    }

    public enum Status { COMPLETED, DECLINED }

    public record TransferRequest(@NotNull UUID paymentId, @NotNull String type, @NotNull BigDecimal amount,
                                  String recipientPhone, String recipientBankId, String recipientCardNumber) {
    }

    public record TransferResponse(UUID paymentId, Status status, String externalId, String reason) {
    }
}
