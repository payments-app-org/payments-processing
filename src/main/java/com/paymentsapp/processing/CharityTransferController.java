package com.paymentsapp.processing;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Заглушка: перевод фонду накопленного за день (округления и пожертвования) одним платёжным поручением.
// Идемпотентно по settlementId: повтор вернёт тот же ответ.
@RestController
@RequestMapping("/api/processing/charity-transfers")
public class CharityTransferController {

    private final Map<UUID, Result> results = new ConcurrentHashMap<>();

    @PostMapping
    public Result transfer(@RequestBody Request req) {
        return results.computeIfAbsent(req.settlementId(),
                id -> new Result(id, "COMPLETED", "CHR-" + Long.toString(Math.abs(id.getMostSignificantBits()), 36).toUpperCase(), null));
    }

    public record Request(UUID settlementId, String fundCode, String fundName, String inn, BigDecimal amount) {
    }

    public record Result(UUID settlementId, String status, String externalId, String reason) {
    }
}
