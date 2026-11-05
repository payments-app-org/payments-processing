package com.paymentsapp.processing;

import java.time.Duration;
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
        int slowKopecks
) {
}
