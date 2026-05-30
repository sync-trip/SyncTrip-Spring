package com.sync.algorithm;

import com.sync.algorithm.step1.Step1Input;
import com.sync.algorithm.step1.Step1Result;
import com.sync.algorithm.step1.WeightedCostFunction;
import com.sync.algorithm.step2.KMeansClustering;
import com.sync.algorithm.step2.Step2Input;
import com.sync.algorithm.step2.Step2Result;
import com.sync.algorithm.step3.SimpleTsp;
import com.sync.algorithm.step3.Step3Input;
import com.sync.algorithm.step3.Step3Result;

/**
 * 알고리즘 파이프라인 진입점.
 *
 * Step1 (WeightedCostFunction)
 *   → Step2 (KMeansClustering)
 *   → Step3 (SimpleTsp)
 *
 * 핵심 규칙:
 * - 순수 함수 — DB 접근 없음 (DB 작업은 호출하는 서비스 레이어에서 수행)
 * - K-Means 재실행 금지 — compute() 호출은 최초 1회, 슬롯 교체는 ScheduleService.getPlanBRecommendations() 사용
 */
public final class AlgorithmService {

    private AlgorithmService() {}

    public static AlgorithmResult compute(AlgorithmInput input) {
        // ── Step 1: 투표 점수 계산 + mainPool / altPool 분리 ────────────
        Step1Input s1in = new Step1Input(
                input.group(), input.members(), input.places(), input.votes());
        Step1Result s1out = WeightedCostFunction.compute(s1in);

        // ── Step 2: K-Means 클러스터링 — 날짜별 장소 배분 ───────────────
        Step2Input s2in = new Step2Input(s1out, input.places());
        Step2Result s2out = KMeansClustering.cluster(s2in);

        // ── Step 3: Nearest Neighbor TSP + 시간 할당 ────────────────────
        Step3Input s3in = new Step3Input(
                s2out,
                input.group().isOverseas(),
                input.dayStartTime(),
                input.openingHoursById(),
                input.group().travelStyle(),          // 식사 윈도우 결정용
                input.group().accommodationLat(),     // 숙소 출발 좌표
                input.group().accommodationLng());    // null이면 첫 장소 출발
        Step3Result s3out = SimpleTsp.schedule(s3in);

        return new AlgorithmResult(s3out, s1out);
    }
}
