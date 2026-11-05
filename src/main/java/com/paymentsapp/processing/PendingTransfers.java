package com.paymentsapp.processing;

import com.paymentsapp.platform.redis.IdempotentResults;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

// Очередь долгих переводов в Redis: sorted set «paymentId → когда готов итог» и сам будущий итог отдельным ключом.
// Переживает рестарт; при нескольких подах перевод забирает тот, чей ZREM вернул 1.
@Component
class PendingTransfers {

    private static final String QUEUE = "processing:pending";
    private static final String ATTEMPTS = "processing:pending-attempts";

    private final StringRedisTemplate redis;
    private final IdempotentResults<ProcessingController.TransferResponse> finals;
    private final ProcessingProperties props;
    private final Clock clock;

    PendingTransfers(StringRedisTemplate redis, IdempotentResults<ProcessingController.TransferResponse> pendingResults,
                     ProcessingProperties props, Clock clock) {
        this.redis = redis;
        this.finals = pendingResults;
        this.props = props;
        this.clock = clock;
    }

    void schedule(ProcessingController.TransferResponse result) {
        finals.putIfAbsent(result.paymentId(), result);
        long min = props.async().delayMin().toMillis();
        long max = props.async().delayMax().toMillis();
        long delay = min + (max > min ? ThreadLocalRandom.current().nextLong(max - min) : 0);
        redis.opsForZSet().add(QUEUE, result.paymentId().toString(), clock.millis() + delay);
    }

    // Переводы, итог которых уже готов
    List<UUID> due() {
        var ids = redis.opsForZSet().rangeByScore(QUEUE, 0, clock.millis(), 0, props.async().batchSize());
        return ids == null ? List.of() : ids.stream().map(UUID::fromString).toList();
    }

    Optional<ProcessingController.TransferResponse> finalResult(UUID id) {
        return finals.find(id);
    }

    // true — перевод забрал этот под
    boolean claim(UUID id) {
        Long removed = redis.opsForZSet().remove(QUEUE, id.toString());
        return removed != null && removed == 1;
    }

    // Уведомление не дошло: повторим позже; false — попытки кончились
    boolean retryLater(UUID id) {
        Long attempt = redis.opsForHash().increment(ATTEMPTS, id.toString(), 1);
        if (attempt >= props.async().notifyAttempts()) {
            done(id);
            return false;
        }
        Duration pause = props.async().notifyRetryDelay().multipliedBy(attempt);
        redis.opsForZSet().add(QUEUE, id.toString(), clock.millis() + pause.toMillis());
        return true;
    }

    void done(UUID id) {
        redis.opsForHash().delete(ATTEMPTS, id.toString());
    }
}
