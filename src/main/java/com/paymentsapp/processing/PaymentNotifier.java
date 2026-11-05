package com.paymentsapp.processing;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.registry.ImportHttpServices;

// Вызовы payment от имени процессинга: итог долгого перевода и входящий перевод по СБП.
// Адрес и тайм-ауты — spring.http.serviceclient.payment.*. Повторы — не здесь: AsyncCompletion переставляет
// уведомление в очередь, а входящий перевод повторит тот, кто его вызвал.
@Component
class PaymentNotifier {

    private final PaymentApi api;

    PaymentNotifier(PaymentApi api) {
        this.api = api;
    }

    void result(ProcessingController.TransferResponse result) {
        api.result(result);
    }

    IncomingResponse incoming(IncomingRequest request) {
        return api.incoming(request);
    }

    @HttpExchange
    interface PaymentApi {

        @PostExchange("/api/internal/processing/results")
        void result(@RequestBody ProcessingController.TransferResponse result);

        @PostExchange("/api/internal/sbp/incoming")
        IncomingResponse incoming(@RequestBody IncomingRequest request);
    }

    @Configuration
    @ImportHttpServices(group = "payment", types = PaymentApi.class)
    static class Clients {
    }

    record IncomingRequest(String operationId, String senderName, String senderBankId, String recipientPhone,
                           BigDecimal amount, String message) {
    }

    record IncomingResponse(UUID paymentId, String decision, String reason) {
    }
}
