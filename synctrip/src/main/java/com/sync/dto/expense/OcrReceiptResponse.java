package com.sync.dto.expense;

import java.math.BigDecimal;
import java.util.List;

public record OcrReceiptResponse(
        String storeName,
        String currency,
        BigDecimal total,
        List<OcrItemResult> items,
        String ocrRaw
) {
}