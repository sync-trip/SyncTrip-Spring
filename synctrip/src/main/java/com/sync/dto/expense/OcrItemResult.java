package com.sync.dto.expense;

import java.math.BigDecimal;

public record OcrItemResult(
        String itemNameOriginal,
        String itemNameKo,
        BigDecimal amount
) {
}