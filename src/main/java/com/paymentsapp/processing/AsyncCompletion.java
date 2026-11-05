package com.paymentsapp.processing;

import com.paymentsapp.platform.redis.IdempotentResults;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Завершает долгие переводы, когда подошёл срок: окончательный итог — в Redis (его отдаст запрос статуса),
// затем уведомление payment. Порядок важен: сначала итог, потом уведомление — если под упадёт между ними,
// payment узнает итог запросом статуса.
@Component
class AsyncCompletion {

    private static final Logger log = LoggerFactory.getLogger(AsyncCompletion.class);

    private final PendingTransfers pending;
    private final IdempotentResults<ProcessingController.TransferResponse> results;
    private final PaymentNotifier payment;

    AsyncCompletion(PendingTransfers pending, IdempotentResults<ProcessingController.TransferResponse> transferResults,
                    PaymentNotifier payment) {
        this.pending = pending;
        this.results = transferResults;
        this.payment = payment;
    }

    @Scheduled(fixedDelayString = "${processing.async.poll-interval}")
    void completeDue() {
        for (UUID id : pending.due()) {
            pending.finalResult(id).ifPresent(result -> results.replace(id, result));
            if (!pending.claim(id)) {
                continue;
            }
            pending.finalResult(id).ifPresent(result -> notify(id, result));
        }
    }

    private void notify(UUID id, ProcessingController.TransferResponse result) {
        try {
            payment.result(result);
            pending.done(id);
            log.info("payment {}: итог {} отправлен в payment", id, result.status());
        } catch (RuntimeException e) {
            // Любой сбой уведомления (сеть, 5xx, ошибка настройки) — перевод возвращается в очередь
            boolean again = pending.retryLater(id);
            log.warn("payment {}: payment не принял итог ({}){}", id, e.getMessage(),
                    again ? ", повторим" : ", попытки кончились — payment узнает итог запросом статуса");
        }
    }
}
