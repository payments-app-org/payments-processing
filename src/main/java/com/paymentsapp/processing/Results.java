package com.paymentsapp.processing;

import com.paymentsapp.platform.redis.IdempotentResults;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

// Итоги переводов в Redis: переживают рестарт и одни на все поды
@Configuration
class Results {

    private static final String TRANSFERS = "processing:transfer:";
    private static final String CHARITY = "processing:charity:";
    private static final String PENDING = "processing:pending-result:";

    @Bean
    IdempotentResults<ProcessingController.TransferResponse> transferResults(StringRedisTemplate redis, JsonMapper json,
                                                                            ProcessingProperties props) {
        return new IdempotentResults<>(redis, json, TRANSFERS, props.resultsTtl(), ProcessingController.TransferResponse.class);
    }

    // Будущий итог долгого перевода: решён при приёме, отдаётся, когда подошёл срок
    @Bean
    IdempotentResults<ProcessingController.TransferResponse> pendingResults(StringRedisTemplate redis, JsonMapper json,
                                                                           ProcessingProperties props) {
        return new IdempotentResults<>(redis, json, PENDING, props.resultsTtl(), ProcessingController.TransferResponse.class);
    }

    @Bean
    IdempotentResults<CharityTransferController.Result> charityResults(StringRedisTemplate redis, JsonMapper json,
                                                                      ProcessingProperties props) {
        return new IdempotentResults<>(redis, json, CHARITY, props.resultsTtl(), CharityTransferController.Result.class);
    }
}
