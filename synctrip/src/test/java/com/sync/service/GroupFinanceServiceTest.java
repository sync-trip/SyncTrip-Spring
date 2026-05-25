package com.sync.service;

import com.sync.domain.band.Band;
import com.sync.domain.band.TravelStyle;
import com.sync.domain.expense.Expense;
import com.sync.domain.finance.GroupExchangeRate;
import com.sync.domain.finance.GroupFinance;
import com.sync.domain.user.User;
import com.sync.dto.finance.GroupFinanceCurrencyRequest;
import com.sync.dto.finance.GroupFinanceResponse;
import com.sync.repository.BandMemberRepository;
import com.sync.repository.BandRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupFinanceServiceTest {

    @Mock private GroupFinanceRepository groupFinanceRepository;
    @Mock private GroupExchangeRateRepository groupExchangeRateRepository;
    @Mock private BandRepository bandRepository;
    @Mock private BandMemberRepository bandMemberRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private ExchangeRateApiService exchangeRateApiService;

    private GroupFinanceService groupFinanceService;

    @BeforeEach
    void setUp() {
        groupFinanceService = new GroupFinanceService(
                groupFinanceRepository, groupExchangeRateRepository,
                bandRepository, bandMemberRepository,
                expenseRepository, exchangeRateApiService
        );
    }

    @Test
    void getFinance_forbidden_whenNotBandMember() {
        when(bandMemberRepository.existsByBandIdAndUserId(1L, 99L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> groupFinanceService.getFinance(99L, 1L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getFinance_returnsExistingFinance() {
        User owner = makeUser(1L, "A");
        Band band = makeBand(10L, owner);
        GroupFinance finance = GroupFinance.create(band, "KRW");

        when(bandMemberRepository.existsByBandIdAndUserId(10L, 1L)).thenReturn(true);
        when(groupFinanceRepository.findByBandId(10L)).thenReturn(Optional.of(finance));
        when(groupExchangeRateRepository.findByBandId(10L)).thenReturn(List.of());
        when(expenseRepository.findByBandIdAndIsDeletedFalseOrderByPaidAtDesc(10L)).thenReturn(List.of());

        GroupFinanceResponse response = groupFinanceService.getFinance(1L, 10L);

        assertThat(response.baseCurrency()).isEqualTo("KRW");
        assertThat(response.totalInBaseCurrency()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.exchangeRates()).isEmpty();
    }

    @Test
    void getFinance_createsFinanceWhenNotExists() {
        User owner = makeUser(1L, "A");
        Band band = makeBand(10L, owner);

        when(bandMemberRepository.existsByBandIdAndUserId(10L, 1L)).thenReturn(true);
        when(groupFinanceRepository.findByBandId(10L)).thenReturn(Optional.empty());
        when(bandRepository.findByIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(band));
        when(groupFinanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(groupExchangeRateRepository.findByBandId(10L)).thenReturn(List.of());
        when(expenseRepository.findByBandIdAndIsDeletedFalseOrderByPaidAtDesc(10L)).thenReturn(List.of());

        GroupFinanceResponse response = groupFinanceService.getFinance(1L, 10L);

        assertThat(response.baseCurrency()).isEqualTo("KRW"); // KR → KRW
        verify(groupFinanceRepository).save(any(GroupFinance.class));
    }

    @Test
    void updateBaseCurrency_forbidden_whenNotOwner() {
        User owner = makeUser(1L, "A");
        User other = makeUser(2L, "B");
        Band band = makeBand(10L, owner);

        when(bandRepository.findByIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(band));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> groupFinanceService.updateBaseCurrency(2L, 10L,
                        new GroupFinanceCurrencyRequest("USD")));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void updateBaseCurrency_updatesSuccessfully() {
        User owner = makeUser(1L, "A");
        Band band = makeBand(10L, owner);
        GroupFinance finance = GroupFinance.create(band, "KRW");

        when(bandRepository.findByIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(band));
        when(groupFinanceRepository.findByBandId(10L)).thenReturn(Optional.of(finance));
        when(groupFinanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(groupExchangeRateRepository.findByBandId(10L)).thenReturn(List.of());
        when(expenseRepository.findByBandIdAndIsDeletedFalseOrderByPaidAtDesc(10L)).thenReturn(List.of());

        GroupFinanceResponse response = groupFinanceService.updateBaseCurrency(1L, 10L,
                new GroupFinanceCurrencyRequest("USD"));

        assertThat(response.baseCurrency()).isEqualTo("USD");
    }

    @Test
    void getFinance_totalCalculated_withExchangeRate() {
        User owner = makeUser(1L, "A");
        Band band = makeBand(10L, owner);
        GroupFinance finance = GroupFinance.create(band, "KRW");

        // 1 KRW = 0.001 USD → 1 USD = 1000 KRW, 10 USD = 10000 KRW
        GroupExchangeRate usdRate = GroupExchangeRate.create(band, "USD", new BigDecimal("0.001"));
        Expense e1 = Expense.create(band, owner, "숙박", BigDecimal.valueOf(5000), "KRW", null, null, null);
        Expense e2 = Expense.create(band, owner, "식사", BigDecimal.TEN, "USD", null, null, null);

        when(bandMemberRepository.existsByBandIdAndUserId(10L, 1L)).thenReturn(true);
        when(groupFinanceRepository.findByBandId(10L)).thenReturn(Optional.of(finance));
        when(groupExchangeRateRepository.findByBandId(10L)).thenReturn(List.of(usdRate));
        when(expenseRepository.findByBandIdAndIsDeletedFalseOrderByPaidAtDesc(10L))
                .thenReturn(List.of(e1, e2));

        GroupFinanceResponse response = groupFinanceService.getFinance(1L, 10L);

        // 5000 KRW + 10/0.001 = 5000 + 10000 = 15000 KRW
        assertThat(response.totalInBaseCurrency()).isEqualByComparingTo("15000.00");
    }

    @Test
    void initFinanceForBand_skipsWhenAlreadyExists() {
        User owner = makeUser(1L, "A");
        Band band = makeBand(10L, owner);

        when(groupFinanceRepository.findByBandId(10L))
                .thenReturn(Optional.of(GroupFinance.create(band, "KRW")));

        groupFinanceService.initFinanceForBand(band);

        verify(groupFinanceRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void initFinanceForBand_createsWithCountryCurrency() {
        User owner = makeUser(1L, "A");
        Band band = makeBand(10L, owner); // countryCode = "KR"

        when(groupFinanceRepository.findByBandId(10L)).thenReturn(Optional.empty());
        when(groupFinanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        groupFinanceService.initFinanceForBand(band);

        verify(groupFinanceRepository).save(any(GroupFinance.class));
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
