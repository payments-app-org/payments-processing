package com.paymentsapp.processing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Заглушка справочника СБП (НСПК): в каких банках у владельца телефона есть счёт.
// У тестовых пользователей набор фиксированный, у остальных номеров — стабильный «случайный» по хешу номера.
@RestController
@RequestMapping("/api/processing/sbp")
public class SbpDirectoryController {

    // Банки-участники СБП (те же идентификаторы, что во фронтенде)
    private static final List<String> BANKS = List.of(
            "100000000111", "100000000004", "100000000008", "100000000005", "100000000001", "100000000007",
            "100000000017", "100000000273", "100000000150", "100000000013", "100000000016", "100000000012");

    private static final Map<String, List<String>> KNOWN = Map.of(
            "+79964480000", List.of("100000000004", "100000000111", "100000000008"),  // Артур: Т-Банк, Сбер, Альфа
            "+79161234567", List.of("100000000111", "100000000005", "100000000004"),  // Мария: Сбер, ВТБ, Т-Банк
            "+79031112233", List.of("100000000008", "100000000004", "100000000001"),  // Иван: Альфа, Т-Банк, Газпромбанк
            "+79257654321", List.of("100000000005", "100000000111", "100000000273")); // Ольга: ВТБ, Сбер, Озон

    @GetMapping("/banks")
    public Banks banks(@RequestParam String phone) {
        List<String> known = KNOWN.get(phone);
        if (known != null) {
            return new Banks(phone, known);
        }
        // 3–5 банков, одинаковых при каждом запросе для этого номера
        int h = Math.abs(phone.hashCode());
        int count = 3 + h % 3;
        List<String> banks = new ArrayList<>();
        for (int i = 0; banks.size() < count; i++) {
            String bank = BANKS.get((h / 7 + i * 5) % BANKS.size());
            if (!banks.contains(bank)) {
                banks.add(bank);
            }
        }
        return new Banks(phone, banks);
    }

    public record Banks(String phone, List<String> banks) {
    }
}
