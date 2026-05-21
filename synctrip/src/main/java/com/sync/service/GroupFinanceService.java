package com.sync.service;

import com.sync.domain.band.Band;
import com.sync.domain.band.BandRole;
import com.sync.domain.expense.Expense;
import com.sync.domain.finance.GroupExchangeRate;
import com.sync.domain.finance.GroupFinance;
import com.sync.dto.finance.ExchangeRateInfo;
import com.sync.dto.finance.GroupFinanceCurrencyRequest;
import com.sync.dto.finance.GroupFinanceResponse;
import com.sync.repository.BandMemberRepository;
import com.sync.repository.BandRepository;
import com.sync.repository.ExpenseRepository;
import com.sync.repository.GroupExchangeRateRepository;
import com.sync.repository.GroupFinanceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class GroupFinanceService {

    private static final Map<String, String> COUNTRY_TO_CURRENCY = Map.ofEntries(
            Map.entry("KR", "KRW"), Map.entry("JP", "JPY"), Map.entry("US", "USD"),
            Map.entry("TH", "THB"), Map.entry("TW", "TWD"), Map.entry("CN", "CNY"),
            Map.entry("GB", "GBP"), Map.entry("FR", "EUR"), Map.entry("DE", "EUR"),
            Map.entry("IT", "EUR"), Map.entry("ES", "EUR"), Map.entry("VN", "VND"),
            Map.entry("SG", "SGD"), Map.entry("AU", "AUD"), Map.entry("CA", "CAD"),
            Map.entry("HK", "HKD"), Map.entry("MY", "MYR"), Map.entry("PH", "PHP"),
            Map.entry("ID", "IDR"), Map.entry("IN", "INR"), Map.entry("TR", "TRY")
    );

    private final GroupFinanceRepository groupFinanceRepository;
    private final GroupExchangeRateRepository groupExchangeRateRepository;
    private final BandRepository bandRepository;
    private final BandMemberRepository bandMemberRepository;
    private final ExpenseRepository expenseRepository;
    private final ExchangeRateApiService exchangeRateApiService;

    public GroupFinanceService(GroupFinanceRepository groupFinanceRepository,
                               GroupExchangeRateRepository groupExchangeRateRepository,
                               BandRepository bandRepository,
                               BandMemberRepository bandMemberRepository,
                               ExpenseRepository expenseRepository,
                               ExchangeRateApiService exchangeRateApiService) {
        this.groupFinanceRepository = groupFinanceRepository;
        this.groupExchangeRateRepository = groupExchangeRateRepository;
        this.bandRepository = bandRepository;
        this.bandMemberRepository = bandMemberRepository;
        this.expenseRepository = expenseRepository;
        this.exchangeRateApiService = exchangeRateApiService;
    }

    @Transactional(readOnly = true)
    public GroupFinanceResponse getFinance(Long userId, Long bandId) {
        validateBandMember(bandId, userId);
        GroupFinance finance = getOrCreateFinance(bandId);
        return buildResponse(finance, bandId);
    }

    public GroupFinanceResponse updateBaseCurrency(Long userId, Long bandId, GroupFinanceCurrencyRequest request) {
        validateOwner(userId, bandId);
        GroupFinance finance = getOrCreateFinance(bandId);
        finance.updateBaseCurrency(request.baseCurrency().toUpperCase());
        groupFinanceRepository.save(finance);
        return buildResponse(finance, bandId);
    }

    public GroupFinanceResponse refreshRates(Long userId, Long bandId) {
        validateBandMember(bandId, userId);
        GroupFinance finance = getOrCreateFinance(bandId);
        String baseCurrency = finance.getBaseCurrency();

        Band band = bandRepository.findByIdAndIsDeletedFalse(bandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "밴드를 찾을 수 없습니다."));

        Map<String, BigDecimal> rates = exchangeRateApiService.fetchRates(baseCurrency);

        rates.forEach((currency, rate) -> {
            if (currency.equals(baseCurrency)) return;
            groupExchangeRateRepository.findByBandIdAndCurrency(bandId, currency)
                    .ifPresentOrElse(
                            existing -> {
                                existing.updateRate(rate);
                                groupExchangeRateRepository.save(existing);
                            },
                            () -> groupExchangeRateRepository.save(
                                    GroupExchangeRate.create(band, currency, rate))
                    );
        });

        return buildResponse(finance, bandId);
    }

    // 밴드 생성 시 country_code 기반으로 자동 초기화 (BandService에서 호출)
    public void initFinanceForBand(Band band) {
        if (groupFinanceRepository.findByBandId(band.getId()).isPresent()) return;
        String currency = COUNTRY_TO_CURRENCY.getOrDefault(band.getCountryCode(), "KRW");
        groupFinanceRepository.save(GroupFinance.create(band, currency));
    }

    private GroupFinance getOrCreateFinance(Long bandId) {
        return groupFinanceRepository.findByBandId(bandId).orElseGet(() -> {
            Band band = bandRepository.findByIdAndIsDeletedFalse(bandId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "밴드를 찾을 수 없습니다."));
            String currency = COUNTRY_TO_CURRENCY.getOrDefault(band.getCountryCode(), "KRW");
            return groupFinanceRepository.save(GroupFinance.create(band, currency));
        });
    }

    private GroupFinanceResponse buildResponse(GroupFinance finance, Long bandId) {
        String baseCurrency = finance.getBaseCurrency();

        List<ExchangeRateInfo> rateInfos = groupExchangeRateRepository.findByBandId(bandId)
                .stream()
                .map(r -> new ExchangeRateInfo(r.getCurrency(), r.getExchangeRate(), r.getRateUpdatedAt()))
                .collect(Collectors.toList());

        // 기준 통화로 환산한 총 지출 합계
        Map<String, BigDecimal> rateMap = rateInfos.stream()
                .collect(Collectors.toMap(ExchangeRateInfo::currency, ExchangeRateInfo::rate));

        BigDecimal total = expenseRepository.findByBandIdAndIsDeletedFalseOrderByPaidAtDesc(bandId)
                .stream()
                .map(expense -> convertToBase(expense, baseCurrency, rateMap))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new GroupFinanceResponse(baseCurrency, rateInfos, total.setScale(2, RoundingMode.HALF_UP));
    }

    // 지출 금액을 기준 통화로 환산
    // exchange_rate = 1 baseCurrency = N foreignCurrency 이므로 나누기로 변환
    private BigDecimal convertToBase(Expense expense, String baseCurrency, Map<String, BigDecimal> rateMap) {
        if (expense.getCurrency().equals(baseCurrency)) {
            return expense.getAmount();
        }
        BigDecimal rate = rateMap.get(expense.getCurrency());
        if (rate == null || rate.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return expense.getAmount().divide(rate, 10, RoundingMode.HALF_UP);
    }

    private void validateBandMember(Long bandId, Long userId) {
        if (!bandMemberRepository.existsByBandIdAndUserId(bandId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "밴드 멤버만 접근할 수 있습니다.");
        }
    }

    private void validateOwner(Long userId, Long bandId) {
        Band band = bandRepository.findByIdAndIsDeletedFalse(bandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "밴드를 찾을 수 없습니다."));
        if (!band.getOwner().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "방장만 기준 통화를 변경할 수 있습니다.");
        }
    }
}
