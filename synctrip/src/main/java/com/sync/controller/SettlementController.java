package com.sync.controller;

import com.sync.common.annotation.LoginUser;
import com.sync.dto.finance.SettlementResponse;
import com.sync.service.SettlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bands/{bandId}/settlement")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @GetMapping
    public ResponseEntity<SettlementResponse> calculate(
            @LoginUser Long userId,
            @PathVariable Long bandId
    ) {
        return ResponseEntity.ok(settlementService.calculate(userId, bandId));
    }
}
