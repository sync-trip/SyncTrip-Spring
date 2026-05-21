package com.sync.dto.finance;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExchangeRateInfo(
        String currency,
        BigDecimal rate,
        LocalDateTime updatedAt
) {
}
