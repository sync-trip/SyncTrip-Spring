package com.sync.service;

import com.sync.domain.band.Band;
import com.sync.domain.band.TravelStyle;
import com.sync.domain.expense.Expense;
import com.sync.domain.expense.ExpenseMember;
import com.sync.domain.finance.GroupExchangeRate;
import com.sync.domain.finance.GroupFinance;
import com.sync.domain.user.User;
import com.sync.dto.finance.SettlementResponse;
import com.sync.dto.finance.SettlementTransaction;
import com.sync.repository.BandMemberRepository;
import com.sync.repository.ExpenseMemberRepository;
import com.sync.repository.ExpenseRepository;
import com.sync.repository.GroupExchangeRateRepository;
import com.sync.repository.GroupFinanceRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock private ExpenseRepository expenseRepository;
    @Mock private ExpenseMemberRepository expenseMemberRepository;
    @Mock private GroupFinanceRepository groupFinanceRepository;
    @Mock private GroupExchangeRateRepository groupExchangeRateRepository;
    @Mock private BandMemberRepository bandMemberRepository;

    private SettlementService settlementService;

    @BeforeEach
    void setUp() {
        settlementService = new SettlementService(
                expenseRepository, expenseMemberRepository,
                groupFinanceRepository, groupExchangeRateRepository, bandMemberRepository
        );
    }

    @Test
    void calculate_forbidden_whenNotBandMember() {
        when(bandMemberRepository.existsByBandIdAndUserId(1L, 99L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> settlementService.calculate(99L, 1L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void calculate_emptyExpenses_returnsZeroTotalAndNoTransactions() {
        when(bandMemberRepository.existsByBandIdAndUserId(1L, 1L)).thenReturn(true);
        when(groupFinanceRepository.findByBandId(1L)).thenReturn(Optional.empty());
        when(groupExchangeRateRepository.findByBandId(1L)).thenReturn(List.of());
        when(expenseRepository.findByBandIdAndIsDeletedFalseOrderByPaidAtDesc(1L)).thenReturn(List.of());

        SettlementResponse response = settlementService.calculate(1L, 1L);

        assertThat(response.totalExpense()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.transactions()).isEmpty();
        assertThat(response.memberSummaries()).isEmpty();
    }

    @Test
    void calculate_twoPersonSplit_createsCorrectTransaction() {
        User userA = makeUser(1L, "A");
        User userB = makeUser(2L, "B");
        Band band = makeBand(10L, userA);

        // A가 100원 결제, A·B가 반반 부담
        Expense expense = Expense.create(band, userA, "식사", BigDecimal.valueOf(100), "KRW", null, null, null);
        setId(expense, 100L);

        ExpenseMember emA = ExpenseMember.create(expense, userA);
        ExpenseMember emB = ExpenseMember.create(expense, userB);

        when(bandMemberRepository.existsByBandIdAndUserId(10L, 1L)).thenReturn(true);
        when(groupFinanceRepository.findByBandId(10L))
                .thenReturn(Optional.of(GroupFinance.create(band, "KRW")));
        when(groupExchangeRateRepository.findByBandId(10L)).thenReturn(List.of());
        when(expenseRepository.findByBandIdAndIsDeletedFalseOrderByPaidAtDesc(10L))
                .thenReturn(List.of(expense));
        when(expenseMemberRepository.findByExpenseId(100L)).thenReturn(List.of(emA, emB));

        SettlementResponse response = settlementService.calculate(1L, 10L);

        assertThat(response.totalExpense()).isEqualByComparingTo("100.00");
        assertThat(response.transactions()).hasSize(1);

        SettlementTransaction tx = response.transactions().get(0);
        assertThat(tx.fromUserId()).isEqualTo(2L); // B가 A에게 송금
        assertThat(tx.toUserId()).isEqualTo(1L);
        assertThat(tx.amount()).isEqualByComparingTo("50.00");
    }

    @Test
    void calculate_noExpenseMembers_payerBearsAllAndNoTransaction() {
        User userA = makeUser(1L, "A");
        Band band = makeBand(10L, userA);

        // A가 혼자 100원 결제, 분담자 없음 → 정산 대상 아님
        Expense expense = Expense.create(band, userA, "숙박", BigDecimal.valueOf(100), "KRW", null, null, null);
        setId(expense, 100L);

        when(bandMemberRepository.existsByBandIdAndUserId(10L, 1L)).thenReturn(true);
        when(groupFinanceRepository.findByBandId(10L))
                .thenReturn(Optional.of(GroupFinance.create(band, "KRW")));
        when(groupExchangeRateRepository.findByBandId(10L)).thenReturn(List.of());
        when(expenseRepository.findByBandIdAndIsDeletedFalseOrderByPaidAtDesc(10L))
                .thenReturn(List.of(expense));
        when(expenseMemberRepository.findByExpenseId(100L)).thenReturn(List.of());

        SettlementResponse response = settlementService.calculate(1L, 10L);

        assertThat(response.totalExpense()).isEqualByComparingTo("100.00");
        assertThat(response.transactions()).isEmpty();
    }

    @Test
    void calculate_threePersonMultipleExpenses_minimizesTransactions() {
        User userA = makeUser(1L, "A");
        User userB = makeUser(2L, "B");
        User userC = makeUser(3L, "C");
        Band band = makeBand(10L, userA);

        // A가 120원 결제 (A·B·C 분담 → 각 40)
        Expense e1 = Expense.create(band, userA, "식사", BigDecimal.valueOf(120), "KRW", null, null, null);
        setId(e1, 101L);
        // B가 60원 결제 (A·B·C 분담 → 각 20)
        Expense e2 = Expense.create(band, userB, "교통", BigDecimal.valueOf(60), "KRW", null, null, null);
        setId(e2, 102L);

        when(bandMemberRepository.existsByBandIdAndUserId(10L, 1L)).thenReturn(true);
        when(groupFinanceRepository.findByBandId(10L))
                .thenReturn(Optional.of(GroupFinance.create(band, "KRW")));
        when(groupExchangeRateRepository.findByBandId(10L)).thenReturn(List.of());
        when(expenseRepository.findByBandIdAndIsDeletedFalseOrderByPaidAtDesc(10L))
                .thenReturn(List.of(e1, e2));
        when(expenseMemberRepository.findByExpenseId(101L))
                .thenReturn(List.of(ExpenseMember.create(e1, userA),
                        ExpenseMember.create(e1, userB), ExpenseMember.create(e1, userC)));
        when(expenseMemberRepository.findByExpenseId(102L))
                .thenReturn(List.of(ExpenseMember.create(e2, userA),
                        ExpenseMember.create(e2, userB), ExpenseMember.create(e2, userC)));

        SettlementResponse response = settlementService.calculate(1L, 10L);

        // A: paid=120, share=60 → net=+60
        // B: paid=60,  share=60 → net=0
        // C: paid=0,   share=60 → net=-60
        assertThat(response.totalExpense()).isEqualByComparingTo("180.00");
        assertThat(response.transactions()).hasSize(1);

        SettlementTransaction tx = response.transactions().get(0);
        assertThat(tx.fromUserId()).isEqualTo(3L); // C → A
        assertThat(tx.toUserId()).isEqualTo(1L);
        assertThat(tx.amount()).isEqualByComparingTo("60.00");
    }

    @Test
    void calculate_multiCurrency_convertsToBase() {
        User userA = makeUser(1L, "A");
        User userB = makeUser(2L, "B");
        Band band = makeBand(10L, userA);

        // 1 KRW = 0.001 USD → 1 USD = 1000 KRW
        // A가 1 USD 결제, A·B 분담 → 각 500 KRW 부담
        Expense expense = Expense.create(band, userA, "커피", BigDecimal.ONE, "USD", null, null, null);
        setId(expense, 100L);

        GroupExchangeRate usdRate = GroupExchangeRate.create(band, "USD", new BigDecimal("0.001"));

        when(bandMemberRepository.existsByBandIdAndUserId(10L, 1L)).thenReturn(true);
        when(groupFinanceRepository.findByBandId(10L))
                .thenReturn(Optional.of(GroupFinance.create(band, "KRW")));
        when(groupExchangeRateRepository.findByBandId(10L)).thenReturn(List.of(usdRate));
        when(expenseRepository.findByBandIdAndIsDeletedFalseOrderByPaidAtDesc(10L))
                .thenReturn(List.of(expense));
        when(expenseMemberRepository.findByExpenseId(100L))
                .thenReturn(List.of(ExpenseMember.create(expense, userA),
                        ExpenseMember.create(expense, userB)));

        SettlementResponse response = settlementService.calculate(1L, 10L);

        // 1 USD = 1000 KRW 총액
        assertThat(response.totalExpense()).isEqualByComparingTo("1000.00");
        assertThat(response.transactions()).hasSize(1);
        assertThat(response.transactions().get(0).amount()).isEqualByComparingTo("500.00");
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
                TravelStyle.PACKED, null, null, null);
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
