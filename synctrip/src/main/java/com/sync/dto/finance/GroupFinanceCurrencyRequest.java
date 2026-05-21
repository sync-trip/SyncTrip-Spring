package com.sync.dto.finance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GroupFinanceCurrencyRequest(
        @NotBlank(message = "통화 코드를 입력해주세요")
        @Size(max = 10, message = "통화 코드는 10자 이하여야 합니다")
        String baseCurrency
) {
}
