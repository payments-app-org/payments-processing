package com.paymentsapp.processing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

// Эмуляция входящего перевода по СБП: «в другом банке отправили деньги на телефон нашего клиента».
// Настоящий СБП присылает такой запрос сам; здесь его запускают вручную (Swagger, curl).
// operationId можно передать, чтобы проверить повтор: тот же номер операции не зачислится второй раз.
@RestController
@RequestMapping("/api/processing/sbp/incoming")
public class IncomingSbpController {

    private static final String OPERATION_PREFIX = "SBP-";
    private static final String DECLINED = "DECLINED";
    private static final String RECIPIENT_NOT_FOUND = "Получатель не найден в банке";

    private final PaymentNotifier payment;

    public IncomingSbpController(PaymentNotifier payment) {
        this.payment = payment;
    }

    @PostMapping
    public PaymentNotifier.IncomingResponse send(@Valid @RequestBody Request req) {
        String operationId = req.operationId() != null ? req.operationId() : OPERATION_PREFIX + UUID.randomUUID();
        try {
            return payment.incoming(new PaymentNotifier.IncomingRequest(operationId, req.senderName(), req.senderBankId(),
                    req.recipientPhone(), req.amount(), req.message()));
        } catch (HttpClientErrorException.NotFound e) {
            // Нет такого клиента в банке получателя — СБП отказывает отправителю
            return new PaymentNotifier.IncomingResponse(null, DECLINED, RECIPIENT_NOT_FOUND);
        }
    }

    public record Request(
            @Size(max = 100) String operationId,
            @NotBlank @Size(max = 100) String senderName,
            @NotNull @Pattern(regexp = "\\d{12}", message = "идентификатор банка СБП — 12 цифр") String senderBankId,
            @NotNull @Pattern(regexp = "\\+7\\d{10}", message = "телефон в формате +7XXXXXXXXXX") String recipientPhone,
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
            @Size(max = 40) String message) {
    }
}
