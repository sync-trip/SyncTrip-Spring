package com.sync.service;

import com.sync.domain.band.Band;
import com.sync.domain.band.TravelStyle;
import com.sync.domain.expense.Expense;
import com.sync.domain.expense.ExpenseMember;
import com.sync.domain.user.User;
import com.sync.dto.expense.ExpenseCreateRequest;
import com.sync.dto.expense.ExpenseResponse;
import com.sync.dto.expense.ExpenseUpdateRequest;
import com.sync.repository.BandMemberRepository;
import com.sync.repository.BandRepository;
import com.sync.repository.ExpenseMemberRepository;
import com.sync.repository.ExpenseRepository;
import com.sync.repository.UserRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock private ExpenseRepository expenseRepository;
    @Mock private ExpenseMemberRepository expenseMemberRepository;
    @Mock private BandRepository bandRepository;
    @Mock private BandMemberRepository bandMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private GeminiOcrService geminiOcrService;

    private ExpenseService expenseService;

    @BeforeEach
    void setUp() {
        expenseService = new ExpenseService(
                expenseRepository, expenseMemberRepository,
                bandRepository, bandMemberRepository,
                userRepository, geminiOcrService
        );
    }

    @Test
    void createExpense_forbidden_whenNotBandMember() {
        when(bandMemberRepository.existsByBandIdAndUserId(1L, 99L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> expenseService.createExpense(99L, 1L,
                        new ExpenseCreateRequest("식사", BigDecimal.valueOf(10000), "KRW",
                                null, null, null, null)));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createExpense_savesExpenseWithNoMembers() {
        User userA = makeUser(1L, "A");
        Band band = makeBand(10L, userA);

        when(bandMemberRepository.existsByBandIdAndUserId(10L, 1L)).thenReturn(true);
        when(bandRepository.findByIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(band));
        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(userA));
        when(expenseRepository.save(any())).thenAnswer(i -> {
            Expense e = i.getArgument(0);
            setId(e, 100L);
            return e;
        });

        ExpenseResponse response = expenseService.createExpense(1L, 10L,
                new ExpenseCreateRequest("커피", BigDecimal.valueOf(5000), "KRW",
                        null, null, null, null));

        assertThat(response.payerId()).isEqualTo(1L);
        assertThat(response.itemName()).isEqualTo("커피");
        assertThat(response.amount()).isEqualByComparingTo("5000");
        assertThat(response.currency()).isEqualTo("KRW");
        assertThat(response.memberIds()).isNull();
        verify(expenseMemberRepository, never()).save(any());
    }

    @Test
    void createExpense_savesExpenseMembersWhenProvided() {
        User userA = makeUser(1L, "A");
        User userB = makeUser(2L, "B");
        Band band = makeBand(10L, userA);

        when(bandMemberRepository.existsByBandIdAndUserId(10L, 1L)).thenReturn(true);
        when(bandRepository.findByIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(band));
        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(userA));
        when(userRepository.findByIdAndIsDeletedFalse(2L)).thenReturn(Optional.of(userB));
        when(expenseRepository.save(any())).thenAnswer(i -> {
            Expense e = i.getArgument(0);
            setId(e, 100L);
            return e;
        });
        when(expenseMemberRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ExpenseResponse response = expenseService.createExpense(1L, 10L,
                new ExpenseCreateRequest("식사", BigDecimal.valueOf(20000), "KRW",
                        null, null, null, List.of(1L, 2L)));

        assertThat(response.memberIds()).containsExactly(1L, 2L);
        verify(expenseMemberRepository, org.mockito.Mockito.times(2)).save(any(ExpenseMember.class));
    }

    @Test
    void getExpenses_returnsListForMember() {
        User userA = makeUser(1L, "A");
        Band band = makeBand(10L, userA);

        Expense e1 = Expense.create(band, userA, "점심", BigDecimal.valueOf(8000), "KRW", null, null, null);
        Expense e2 = Expense.create(band, userA, "저녁", BigDecimal.valueOf(12000), "KRW", null, null, null);
        setId(e1, 1L);
        setId(e2, 2L);

        when(bandMemberRepository.existsByBandIdAndUserId(10L, 1L)).thenReturn(true);
        when(expenseRepository.findByBandIdAndIsDeletedFalseOrderByPaidAtDesc(10L))
                .thenReturn(List.of(e2, e1));
        when(expenseMemberRepository.findByExpenseId(1L)).thenReturn(List.of());
        when(expenseMemberRepository.findByExpenseId(2L)).thenReturn(List.of());

        List<ExpenseResponse> result = expenseService.getExpenses(1L, 10L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).itemName()).isEqualTo("저녁");
        assertThat(result.get(1).itemName()).isEqualTo("점심");
    }

    @Test
    void getExpense_forbidden_whenWrongBand() {
        User userA = makeUser(1L, "A");
        Band band10 = makeBand(10L, userA);
        Band band99 = makeBand(99L, userA);

        Expense expense = Expense.create(band10, userA, "식사", BigDecimal.valueOf(5000), "KRW", null, null, null);
        setId(expense, 50L);

        when(bandMemberRepository.existsByBandIdAndUserId(99L, 1L)).thenReturn(true);
        when(expenseRepository.findByIdAndIsDeletedFalse(50L)).thenReturn(Optional.of(expense));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> expenseService.getExpense(1L, 99L, 50L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void updateExpense_forbidden_whenNotPayer() {
        User userA = makeUser(1L, "A");
        User userB = makeUser(2L, "B");
        Band band = makeBand(10L, userA);

        Expense expense = Expense.create(band, userA, "식사", BigDecimal.valueOf(5000), "KRW", null, null, null);
        setId(expense, 50L);

        when(bandMemberRepository.existsByBandIdAndUserId(10L, 2L)).thenReturn(true);
        when(expenseRepository.findByIdAndIsDeletedFalse(50L)).thenReturn(Optional.of(expense));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> expenseService.updateExpense(2L, 10L, 50L,
                        new ExpenseUpdateRequest("수정", BigDecimal.valueOf(6000), "KRW",
                                null, null)));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void updateExpense_updatesSuccessfully() {
        User userA = makeUser(1L, "A");
        Band band = makeBand(10L, userA);

        Expense expense = Expense.create(band, userA, "식사", BigDecimal.valueOf(5000), "KRW", null, null, null);
        setId(expense, 50L);

        when(bandMemberRepository.existsByBandIdAndUserId(10L, 1L)).thenReturn(true);
        when(expenseRepository.findByIdAndIsDeletedFalse(50L)).thenReturn(Optional.of(expense));
        when(expenseRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ExpenseResponse response = expenseService.updateExpense(1L, 10L, 50L,
                new ExpenseUpdateRequest("호텔", BigDecimal.valueOf(100000), "KRW",
                        null, null));

        assertThat(response.itemName()).isEqualTo("호텔");
        assertThat(response.amount()).isEqualByComparingTo("100000");
    }

    @Test
    void deleteExpense_forbidden_whenNotPayer() {
        User userA = makeUser(1L, "A");
        User userB = makeUser(2L, "B");
        Band band = makeBand(10L, userA);

        Expense expense = Expense.create(band, userA, "식사", BigDecimal.valueOf(5000), "KRW", null, null, null);
        setId(expense, 50L);

        when(bandMemberRepository.existsByBandIdAndUserId(10L, 2L)).thenReturn(true);
        when(expenseRepository.findByIdAndIsDeletedFalse(50L)).thenReturn(Optional.of(expense));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> expenseService.deleteExpense(2L, 10L, 50L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deleteExpense_softDeletesExpense() {
        User userA = makeUser(1L, "A");
        Band band = makeBand(10L, userA);

        Expense expense = Expense.create(band, userA, "식사", BigDecimal.valueOf(5000), "KRW", null, null, null);
        setId(expense, 50L);

        when(bandMemberRepository.existsByBandIdAndUserId(10L, 1L)).thenReturn(true);
        when(expenseRepository.findByIdAndIsDeletedFalse(50L)).thenReturn(Optional.of(expense));
        when(expenseRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        expenseService.deleteExpense(1L, 10L, 50L);

        assertThat(expense.isDeleted()).isTrue();
        assertThat(expense.getDeletedAt()).isNotNull();
        verify(expenseRepository).save(expense);
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    private User makeUser(Long id, String name) {
        User u = User.kakaoUser(name + "@test.com", name, null, id.toString());
        setId(u, id);
        return u;
    }

    private Band makeBand(Long id, User owner) {
        Band b = Band.create(owner, "테스트밴드", "제주도",
                33.4, 126.5, "KR", false,
                LocalDate.now(), LocalDate.now().plusDays(3),
                TravelStyle.PACKED, null, null, null, null);
        setId(b, id);
        return b;
    }

    private void setId(Object target, Long id) {
        try {
            Field field = findField(target.getClass(), "id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) return findField(clazz.getSuperclass(), name);
            throw e;
        }
    }
}
