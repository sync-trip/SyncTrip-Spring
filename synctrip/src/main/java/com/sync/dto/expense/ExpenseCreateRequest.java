package com.sync.dto.expense;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ExpenseCreateRequest(
        @NotBlank(message = "항목명을 입력해주세요")
        String itemName,

        @NotNull(message = "금액을 입력해주세요")
        BigDecimal amount,

        @NotBlank(message = "통화를 입력해주세요")
        String currency,

        String receiptUrl,

        String ocrRaw,

        LocalDateTime paidAt,

        List<Long> memberIds
) {
}