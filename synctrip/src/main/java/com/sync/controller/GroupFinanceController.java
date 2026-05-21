package com.sync.controller;

import com.sync.common.annotation.LoginUser;
import com.sync.dto.finance.GroupFinanceCurrencyRequest;
import com.sync.dto.finance.GroupFinanceResponse;
import com.sync.service.GroupFinanceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bands/{bandId}/finance")
public class GroupFinanceController {

    private final GroupFinanceService groupFinanceService;

    public GroupFinanceController(GroupFinanceService groupFinanceService) {
        this.groupFinanceService = groupFinanceService;
    }

    @GetMapping
    public ResponseEntity<GroupFinanceResponse> getFinance(
            @LoginUser Long userId,
            @PathVariable Long bandId
    ) {
        return ResponseEntity.ok(groupFinanceService.getFinance(userId, bandId));
    }

    @PutMapping("/currency")
    public ResponseEntity<GroupFinanceResponse> updateBaseCurrency(
            @LoginUser Long userId,
            @PathVariable Long bandId,
            @Valid @RequestBody GroupFinanceCurrencyRequest request
    ) {
        return ResponseEntity.ok(groupFinanceService.updateBaseCurrency(userId, bandId, request));
    }

    @PostMapping("/rates/refresh")
    public ResponseEntity<GroupFinanceResponse> refreshRates(
            @LoginUser Long userId,
            @PathVariable Long bandId
    ) {
        return ResponseEntity.ok(groupFinanceService.refreshRates(userId, bandId));
    }
}
