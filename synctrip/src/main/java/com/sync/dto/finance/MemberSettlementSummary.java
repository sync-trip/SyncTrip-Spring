package com.sync.dto.finance;

import java.math.BigDecimal;

public record MemberSettlementSummary(
        Long userId,
        String userName,
        BigDecimal totalPaid,
        BigDecimal totalShare,
        BigDecimal netAmount   // 양수=받을 돈, 음수=보낼 돈
) {
}
