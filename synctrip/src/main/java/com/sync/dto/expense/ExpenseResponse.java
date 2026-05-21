package com.sync.dto.expense;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ExpenseResponse(
        Long id,
        Long payerId,
        String payerName,
        String itemName,
        BigDecimal amount,
        String currency,
        String receiptUrl,
        LocalDateTime paidAt,
        List<Long> memberIds
) {
}
