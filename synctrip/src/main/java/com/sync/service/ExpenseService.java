package com.sync.service;

import com.sync.domain.band.Band;
import com.sync.domain.expense.Expense;
import com.sync.domain.expense.ExpenseMember;
import com.sync.domain.user.User;
import com.sync.dto.expense.ExpenseCreateRequest;
import com.sync.dto.expense.ExpenseResponse;
import com.sync.dto.expense.OcrReceiptResponse;
import com.sync.repository.BandMemberRepository;
import com.sync.repository.BandRepository;
import com.sync.repository.ExpenseMemberRepository;
import com.sync.repository.ExpenseRepository;
import com.sync.repository.UserRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseMemberRepository expenseMemberRepository;
    private final BandRepository bandRepository;
    private final BandMemberRepository bandMemberRepository;
    private final UserRepository userRepository;
    private final GeminiOcrService geminiOcrService;

    public ExpenseService(ExpenseRepository expenseRepository,
                          ExpenseMemberRepository expenseMemberRepository,
                          BandRepository bandRepository,
                          BandMemberRepository bandMemberRepository,
                          UserRepository userRepository,
                          GeminiOcrService geminiOcrService) {
        this.expenseRepository = expenseRepository;
        this.expenseMemberRepository = expenseMemberRepository;
        this.bandRepository = bandRepository;
        this.bandMemberRepository = bandMemberRepository;
        this.userRepository = userRepository;
        this.geminiOcrService = geminiOcrService;
    }

    @Transactional(readOnly = true)
    public OcrReceiptResponse scanReceipt(Long userId, Long bandId, MultipartFile imageFile) {
        validateBandMember(bandId, userId);
        return geminiOcrService.ocr(imageFile);
    }

    public ExpenseResponse createExpense(Long userId, Long bandId, ExpenseCreateRequest request) {
        validateBandMember(bandId, userId);

        Band band = bandRepository.findByIdAndIsDeletedFalse(bandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "밴드를 찾을 수 없습니다."));
        User payer = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        Expense expense = Expense.create(
                band, payer,
                request.itemName(), request.amount(), request.currency(),
                request.receiptUrl(), request.ocrRaw(), request.paidAt()
        );
        expenseRepository.save(expense);

        if (request.memberIds() != null && !request.memberIds().isEmpty()) {
            for (Long memberId : request.memberIds()) {
                User member = userRepository.findByIdAndIsDeletedFalse(memberId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "멤버를 찾을 수 없습니다. id=" + memberId));
                expenseMemberRepository.save(ExpenseMember.create(expense, member));
            }
        }

        return toResponse(expense, request.memberIds());
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> getExpenses(Long userId, Long bandId) {
        validateBandMember(bandId, userId);
        return expenseRepository.findByBandIdAndIsDeletedFalseOrderByPaidAtDesc(bandId)
                .stream()
                .map(e -> {
                    List<Long> memberIds = expenseMemberRepository.findByExpenseId(e.getId())
                            .stream().map(m -> m.getUser().getId()).collect(Collectors.toList());
                    return toResponse(e, memberIds);
                })
                .collect(Collectors.toList());
    }

    public void deleteExpense(Long userId, Long bandId, Long expenseId) {
        validateBandMember(bandId, userId);
        Expense expense = expenseRepository.findByIdAndIsDeletedFalse(expenseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "지출 내역을 찾을 수 없습니다."));

        if (!expense.getBand().getId().equals(bandId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 밴드의 지출이 아닙니다.");
        }
        if (!expense.getPayer().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인이 등록한 지출만 삭제할 수 있습니다.");
        }

        expense.delete();
        expenseRepository.save(expense);
    }

    private void validateBandMember(Long bandId, Long userId) {
        if (!bandMemberRepository.existsByBandIdAndUserId(bandId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "밴드 멤버만 접근할 수 있습니다.");
        }
    }

    private ExpenseResponse toResponse(Expense expense, List<Long> memberIds) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getPayer().getId(),
                expense.getPayer().getName(),
                expense.getItemName(),
                expense.getAmount(),
                expense.getCurrency(),
                expense.getReceiptUrl(),
                expense.getPaidAt(),
                memberIds
        );
    }
}
