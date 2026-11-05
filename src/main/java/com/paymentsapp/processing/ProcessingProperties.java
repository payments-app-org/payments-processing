package com.paymentsapp.processing;

import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Настройки заглушки процессинга (processing.* в application.yml)
@ConfigurationProperties("processing")
public record ProcessingProperties(
        // Доли исходов; остальное — COMPLETED
        double declineRate,
        double timeoutRate,
        // «Долгий ответ» — дольше тайм-аута payment
        Duration slowDelay,
        // Сколько хранить итог перевода: повтор и запрос статуса в этот срок получают тот же ответ
        Duration resultsTtl,
        // Суммы для ручной проверки: копейки → гарантированный отказ / долгий ответ
        int declinedKopecks,
        int slowKopecks,
        Async async
) {

    // Долгие переводы: какие типы (ABROAD) и пополнение из другого банка по СБП; через сколько итог;
    // как часто проверять очередь; сколько раз повторять уведомление и с какой паузой (дальше payment спросит сам)
    public record Async(Set<String> types, boolean sbpPull, Duration delayMin, Duration delayMax, Duration pollInterval,
                        int batchSize, int notifyAttempts, Duration notifyRetryDelay) {
    }
}
