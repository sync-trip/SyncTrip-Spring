package com.sync.dto.finance;

import java.math.BigDecimal;
import java.util.List;

public record GroupFinanceResponse(
        String baseCurrency,
        List<ExchangeRateInfo> exchangeRates,
        BigDecimal totalInBaseCurrency
) {
}
