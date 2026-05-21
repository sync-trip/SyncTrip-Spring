package com.sync.dto.finance;

import java.math.BigDecimal;

public record SettlementTransaction(
        Long fromUserId,
        String fromUserName,
        Long toUserId,
        String toUserName,
        BigDecimal amount
) {
}
