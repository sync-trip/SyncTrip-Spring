package com.sync.dto.finance;

import java.math.BigDecimal;
import java.util.List;

public record SettlementResponse(
        String baseCurrency,
        BigDecimal totalExpense,
        List<MemberSettlementSummary> memberSummaries,
        List<SettlementTransaction> transactions
) {
}
