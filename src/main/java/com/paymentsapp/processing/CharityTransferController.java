package com.paymentsapp.processing;

import com.paymentsapp.platform.redis.IdempotentResults;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Заглушка: перевод фонду накопленного за день (округления и пожертвования) одним платёжным поручением.
// Идемпотентно по settlementId: повтор вернёт тот же ответ (итог — в Redis).
@RestController
@RequestMapping("/api/processing/charity-transfers")
public class CharityTransferController {

    private static final String EXTERNAL_PREFIX = "CHR-";
    private static final int ID_RADIX = Character.MAX_RADIX;

    private final IdempotentResults<Result> results;

    public CharityTransferController(IdempotentResults<Result> results) {
        this.results = results;
    }

    @PostMapping
    public Result transfer(@Valid @RequestBody Request req) {
        UUID id = req.settlementId();
        String externalId = EXTERNAL_PREFIX + Long.toString(Math.abs(id.getMostSignificantBits()), ID_RADIX).toUpperCase();
        return results.putIfAbsent(id, new Result(id, ProcessingController.Status.COMPLETED, externalId, null));
    }

    public record Request(@NotNull UUID settlementId, @NotBlank @Size(max = 40) String fundCode,
                          @NotBlank @Size(max = 150) String fundName, @NotNull @Pattern(regexp = "\\d{10}|\\d{12}", message = "ИНН — 10 или 12 цифр") String inn,
                          @NotNull @Positive @Digits(integer = 15, fraction = 2) BigDecimal amount) {
    }

    public record Result(UUID settlementId, ProcessingController.Status status, String externalId, String reason) {
    }
}
