package com.sync.service;

import com.sync.domain.expense.Expense;
import com.sync.domain.expense.ExpenseMember;
import com.sync.domain.finance.GroupExchangeRate;
import com.sync.domain.finance.GroupFinance;
import com.sync.domain.user.User;
import com.sync.dto.finance.MemberSettlementSummary;
import com.sync.dto.finance.SettlementResponse;
import com.sync.dto.finance.SettlementTransaction;
import com.sync.repository.BandMemberRepository;
import com.sync.repository.ExpenseMemberRepository;
import com.sync.repository.ExpenseRepository;
import com.sync.repository.GroupExchangeRateRepository;
import com.sync.repository.GroupFinanceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class SettlementService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseMemberRepository expenseMemberRepository;
    private final GroupFinanceRepository groupFinanceRepository;
    private final GroupExchangeRateRepository groupExchangeRateRepository;
    private final BandMemberRepository bandMemberRepository;

    public SettlementService(ExpenseRepository expenseRepository,
                             ExpenseMemberRepository expenseMemberRepository,
                             GroupFinanceRepository groupFinanceRepository,
                             GroupExchangeRateRepository groupExchangeRateRepository,
                             BandMemberRepository bandMemberRepository) {
        this.expenseRepository = expenseRepository;
        this.expenseMemberRepository = expenseMemberRepository;
        this.groupFinanceRepository = groupFinanceRepository;
        this.groupExchangeRateRepository = groupExchangeRateRepository;
        this.bandMemberRepository = bandMemberRepository;
    }

    public SettlementResponse calculate(Long userId, Long bandId) {
        if (!bandMemberRepository.existsByBandIdAndUserId(bandId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "밴드 멤버만 접근할 수 있습니다.");
        }

        String baseCurrency = groupFinanceRepository.findByBandId(bandId)
                .map(GroupFinance::getBaseCurrency)
                .orElse("KRW");

        Map<String, BigDecimal> rateMap = groupExchangeRateRepository.findByBandId(bandId)
                .stream()
                .collect(Collectors.toMap(GroupExchangeRate::getCurrency, GroupExchangeRate::getExchangeRate));

        List<Expense> expenses = expenseRepository.findByBandIdAndIsDeletedFalseOrderByPaidAtDesc(bandId);

        // userId → 이름 캐시
        Map<Long, String> nameCache = new HashMap<>();

        // userId → 낸 금액 합계
        Map<Long, BigDecimal> paidMap = new HashMap<>();
        // userId → 부담해야 할 금액 합계
        Map<Long, BigDecimal> shareMap = new HashMap<>();

        for (Expense expense : expenses) {
            BigDecimal amountInBase = convertToBase(expense.getAmount(), expense.getCurrency(), baseCurrency, rateMap);

            User payer = expense.getPayer();
            nameCache.put(payer.getId(), payer.getName());
            paidMap.merge(payer.getId(), amountInBase, BigDecimal::add);

            List<ExpenseMember> members = expenseMemberRepository.findByExpenseId(expense.getId());
            if (members.isEmpty()) {
                // 분담자 없음 → 결제자 혼자 부담 (정산 대상 아님)
                shareMap.merge(payer.getId(), amountInBase, BigDecimal::add);
                continue;
            }

            BigDecimal sharePerPerson = amountInBase.divide(
                    BigDecimal.valueOf(members.size()), 10, RoundingMode.HALF_UP);

            for (ExpenseMember member : members) {
                User u = member.getUser();
                nameCache.put(u.getId(), u.getName());
                shareMap.merge(u.getId(), sharePerPerson, BigDecimal::add);
                // 분담자이지만 지출 낸 기록이 없으면 paidMap에 0 초기화
                paidMap.putIfAbsent(u.getId(), BigDecimal.ZERO);
            }
        }

        // 순 잔액 계산: 낸 금액 - 부담 몫
        Map<Long, BigDecimal> netMap = new HashMap<>();
        for (Long uid : paidMap.keySet()) {
            BigDecimal paid = paidMap.getOrDefault(uid, BigDecimal.ZERO);
            BigDecimal share = shareMap.getOrDefault(uid, BigDecimal.ZERO);
            netMap.put(uid, paid.subtract(share));
        }

        BigDecimal totalExpense = paidMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        List<MemberSettlementSummary> summaries = netMap.entrySet().stream()
                .map(e -> new MemberSettlementSummary(
                        e.getKey(),
                        nameCache.getOrDefault(e.getKey(), "알 수 없음"),
                        paidMap.getOrDefault(e.getKey(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                        shareMap.getOrDefault(e.getKey(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                        e.getValue().setScale(2, RoundingMode.HALF_UP)
                ))
                .collect(Collectors.toList());

        List<SettlementTransaction> transactions = minimizeTransactions(netMap, nameCache);

        return new SettlementResponse(baseCurrency, totalExpense.setScale(2, RoundingMode.HALF_UP),
                summaries, transactions);
    }

    // 최소 송금 횟수 그리디 알고리즘
    // 채권자(양수)와 채무자(음수)를 우선순위 큐로 관리해 최대금액부터 매칭
    private List<SettlementTransaction> minimizeTransactions(
            Map<Long, BigDecimal> netMap, Map<Long, String> nameCache) {

        // 채권자: 받을 금액 내림차순
        PriorityQueue<long[]> creditors = new PriorityQueue<>(
                (a, b) -> Long.compare(b[1], a[1]));
        // 채무자: 보낼 금액 내림차순 (절댓값 기준)
        PriorityQueue<long[]> debtors = new PriorityQueue<>(
                (a, b) -> Long.compare(b[1], a[1]));

        // BigDecimal → 원 단위 long (소수점 반올림)으로 변환해 정수 연산
        netMap.forEach((uid, net) -> {
            long cents = net.setScale(0, RoundingMode.HALF_UP).longValue();
            if (cents > 0) creditors.offer(new long[]{uid, cents});
            else if (cents < 0) debtors.offer(new long[]{uid, -cents});
        });

        List<SettlementTransaction> result = new ArrayList<>();

        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            long[] creditor = creditors.poll();
            long[] debtor = debtors.poll();

            long settle = Math.min(creditor[1], debtor[1]);

            result.add(new SettlementTransaction(
                    debtor[0], nameCache.getOrDefault(debtor[0], "알 수 없음"),
                    creditor[0], nameCache.getOrDefault(creditor[0], "알 수 없음"),
                    BigDecimal.valueOf(settle).setScale(2, RoundingMode.HALF_UP)
            ));

            creditor[1] -= settle;
            debtor[1] -= settle;

            if (creditor[1] > 0) creditors.offer(creditor);
            if (debtor[1] > 0) debtors.offer(debtor);
        }

        return result;
    }

    private BigDecimal convertToBase(BigDecimal amount, String currency,
                                      String baseCurrency, Map<String, BigDecimal> rateMap) {
        if (currency.equals(baseCurrency)) return amount;
        BigDecimal rate = rateMap.get(currency);
        if (rate == null || rate.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return amount.divide(rate, 10, RoundingMode.HALF_UP);
    }
}
