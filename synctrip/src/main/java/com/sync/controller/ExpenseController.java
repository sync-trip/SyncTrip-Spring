package com.sync.controller;

import com.sync.common.annotation.LoginUser;
import com.sync.dto.expense.ExpenseCreateRequest;
import com.sync.dto.expense.ExpenseResponse;
import com.sync.dto.expense.ExpenseUpdateRequest;
import com.sync.dto.expense.OcrReceiptResponse;
import com.sync.service.ExpenseService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/bands/{bandId}/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping(value = "/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OcrReceiptResponse> scanReceipt(
            @LoginUser Long userId,
            @PathVariable Long bandId,
            @RequestPart("image") MultipartFile image
    ) {
        return ResponseEntity.ok(expenseService.scanReceipt(userId, bandId, image));
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> createExpense(
            @LoginUser Long userId,
            @PathVariable Long bandId,
            @Valid @RequestBody ExpenseCreateRequest request
    ) {
        return ResponseEntity.ok(expenseService.createExpense(userId, bandId, request));
    }

    @GetMapping
    public ResponseEntity<List<ExpenseResponse>> getExpenses(
            @LoginUser Long userId,
            @PathVariable Long bandId
    ) {
        return ResponseEntity.ok(expenseService.getExpenses(userId, bandId));
    }

    @GetMapping("/{expenseId}")
    public ResponseEntity<ExpenseResponse> getExpense(
            @LoginUser Long userId,
            @PathVariable Long bandId,
            @PathVariable Long expenseId
    ) {
        return ResponseEntity.ok(expenseService.getExpense(userId, bandId, expenseId));
    }

    @PutMapping("/{expenseId}")
    public ResponseEntity<ExpenseResponse> updateExpense(
            @LoginUser Long userId,
            @PathVariable Long bandId,
            @PathVariable Long expenseId,
            @Valid @RequestBody ExpenseUpdateRequest request
    ) {
        return ResponseEntity.ok(expenseService.updateExpense(userId, bandId, expenseId, request));
    }

    @DeleteMapping("/{expenseId}")
    public ResponseEntity<Void> deleteExpense(
            @LoginUser Long userId,
            @PathVariable Long bandId,
            @PathVariable Long expenseId
    ) {
        expenseService.deleteExpense(userId, bandId, expenseId);
        return ResponseEntity.noContent().build();
    }
}
