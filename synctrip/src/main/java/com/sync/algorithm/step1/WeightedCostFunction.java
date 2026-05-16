package com.sync.algorithm.step1;

import com.sync.algorithm.AlgorithmConstants;
import com.sync.algorithm.PlaceCategory;
import com.sync.algorithm.TravelStyle;

import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public final class WeightedCostFunction {

    private WeightedCostFunction() {}

    public static Step1Result compute(Step1Input input) {
        GroupInfo group = input.group();
        List<MemberInfo> members = input.members();
        List<PlaceInfo> places = input.places();
        List<VoteInfo> votes = input.votes();

        // ── 0. 사전 계산 ────────────────────────────────────────────────
        int totalMembers = members.size();
        double destLat = group.destinationLat();
        double destLng = group.destinationLng();
        // [FIX-16] K = (end_date - start_date).days + 1
        int K = (int) (ChronoUnit.DAYS.between(group.startDate(), group.endDate()) + 1);
        TravelStyle style = group.travelStyle();

        // ── 1. 투표 집계 ────────────────────────────────────────────────
        // [FIX-1]  result=0(BOOKMARK)은 본인 자동 LIKE — like_count에 포함
        // [FIX-15] total_voters = like+dislike (미투표 row는 존재 안 함)
        Map<Long, Integer> likeCount    = new HashMap<>();
        Map<Long, Integer> dislikeCount = new HashMap<>();
        for (PlaceInfo p : places) {
            likeCount.put(p.placeId(), 0);
            dislikeCount.put(p.placeId(), 0);
        }
        for (VoteInfo v : votes) {
            if (v.result() >= 0) {
                likeCount.merge(v.placeId(), 1, Integer::sum);
            } else {
                dislikeCount.merge(v.placeId(), 1, Integer::sum);
            }
        }

        Map<Long, Double> voteScore = new HashMap<>();
        for (PlaceInfo p : places) {
            int lc = likeCount.get(p.placeId());
            int dc = dislikeCount.get(p.placeId());
            int totalVoters = lc + dc;
            if (totalVoters == 0) {
                voteScore.put(p.placeId(), 0.0);
            } else {
                double likeRate    = (double) lc / totalVoters;
                double dislikeRate = (double) dc / totalVoters;
                voteScore.put(p.placeId(), likeRate * 2 - dislikeRate);
            }
        }

        // ── 2. Haversine 거리 + 정규화 ──────────────────────────────────
        // [FIX-14] max_dist < DIST_PENALTY_FLOOR_KM → 거리 페널티 적용 안 함
        Map<Long, Double> distKm = new HashMap<>();
        for (PlaceInfo p : places) {
            distKm.put(p.placeId(), haversine(destLat, destLng, p.latitude(), p.longitude()));
        }
        double maxDist = distKm.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

        boolean skipPenalty = maxDist == 0.0 || maxDist < AlgorithmConstants.DIST_PENALTY_FLOOR_KM;

        Map<Long, Double> normDist = new HashMap<>();
        for (PlaceInfo p : places) {
            normDist.put(p.placeId(), skipPenalty ? 0.0 : distKm.get(p.placeId()) / maxDist);
        }

        Map<Long, Double> priorityScore = new HashMap<>();
        for (PlaceInfo p : places) {
            double ps = voteScore.get(p.placeId()) * AlgorithmConstants.VOTE_SCORE_WEIGHT
                      - normDist.get(p.placeId())  * AlgorithmConstants.DIST_PENALTY_WEIGHT;
            priorityScore.put(p.placeId(), ps);
        }

        // ── 3. 투표 통과 필터 ────────────────────────────────────────────
        // [FIX-2]  절대값 기준 (비율 기반 폐기)
        // [FIX-11] vote_score > 0 컷오프 — 싫어요 테러 장소 altPool 차단
        int passedThreshold = (int) Math.ceil(totalMembers * 0.5);
        int altMin = Math.max(1, passedThreshold - 2);
        int altMax = passedThreshold - 1;

        List<PlaceInfo> passed  = new ArrayList<>();
        List<PlaceInfo> altPool = new ArrayList<>();
        for (PlaceInfo p : places) {
            int lc = likeCount.get(p.placeId());
            if (lc >= passedThreshold) {
                passed.add(p);
            } else if (lc >= altMin && lc <= altMax && voteScore.get(p.placeId()) > 0) {
                altPool.add(p);
            }
            // else: 완전 탈락
        }

        // ── 4. FOOD 쿼터 확보 ────────────────────────────────────────────
        // [FIX-13] 정렬: priority DESC → like_count DESC → dist ASC → place_id ASC
        Comparator<PlaceInfo> cmp = placeComparator(priorityScore, likeCount, distKm);

        List<PlaceInfo> foodPassed = passed.stream()
                .filter(p -> p.category() == PlaceCategory.FOOD)
                .sorted(cmp)
                .collect(Collectors.toList());

        int foodPerDay = style == TravelStyle.PACKED
                ? AlgorithmConstants.PACKED_FOOD_PER_DAY
                : AlgorithmConstants.RELAXED_FOOD_PER_DAY;
        int foodQuota = foodPerDay * K;

        int splitAt = Math.min(foodQuota, foodPassed.size());
        List<PlaceInfo> foodMain = new ArrayList<>(foodPassed.subList(0, splitAt));
        altPool.addAll(foodPassed.subList(splitAt, foodPassed.size()));

        // ── 5. 비FOOD Density 한도 편입 ─────────────────────────────────
        // [FIX-12] priority_score <= 0 → altPool 강등
        int densityLimit  = style == TravelStyle.PACKED
                ? AlgorithmConstants.PACKED_DENSITY
                : AlgorithmConstants.RELAXED_DENSITY;
        int densityBudget = densityLimit * K;

        List<PlaceInfo> nonFood = passed.stream()
                .filter(p -> p.category() != PlaceCategory.FOOD)
                .sorted(cmp)
                .collect(Collectors.toList());

        List<PlaceInfo> mainPool  = new ArrayList<>(foodMain);
        int accumulated = 0;
        for (PlaceInfo p : nonFood) {
            if (priorityScore.get(p.placeId()) <= 0) {
                altPool.add(p);
                continue;
            }
            if (accumulated + p.densityPoint() <= densityBudget) {
                mainPool.add(p);
                accumulated += p.densityPoint();
            } else {
                altPool.add(p);
            }
        }

        // ── 6. 이상치 마킹 + 결과 구성 ─────────────────────────────────
        // [FIX-17] 절대 거리 기반 (OUTLIER_DIST_KM = 30km)
        List<MainPoolPlace> mainPoolResult = mainPool.stream()
                .map(p -> new MainPoolPlace(
                        p.placeId(),
                        priorityScore.get(p.placeId()),
                        voteScore.get(p.placeId()),
                        likeCount.get(p.placeId()),
                        dislikeCount.get(p.placeId()),
                        distKm.get(p.placeId()),
                        distKm.get(p.placeId()) > AlgorithmConstants.OUTLIER_DIST_KM))
                .collect(Collectors.toList());

        // altPool 최종 정렬 + alt_rank 부여 [FIX-13]
        altPool.sort(cmp);
        List<AltPoolPlace> altPoolResult = new ArrayList<>();
        for (int i = 0; i < altPool.size(); i++) {
            PlaceInfo p = altPool.get(i);
            altPoolResult.add(new AltPoolPlace(
                    p.placeId(),
                    priorityScore.get(p.placeId()),
                    voteScore.get(p.placeId()),
                    likeCount.get(p.placeId()),
                    i + 1));
        }

        Step1Meta meta = new Step1Meta(
                K, totalMembers, passedThreshold, densityLimit, foodPerDay,
                maxDist, destLat, destLng);

        return new Step1Result(mainPoolResult, altPoolResult, meta);
    }

    /** Haversine 공식으로 두 좌표 간 거리(km) 계산 */
    static double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return AlgorithmConstants.EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** [FIX-13] priority DESC → like_count DESC → dist ASC → place_id ASC */
    private static Comparator<PlaceInfo> placeComparator(
            Map<Long, Double>  priorityScore,
            Map<Long, Integer> likeCount,
            Map<Long, Double>  distKm) {
        return (a, b) -> {
            int c = Double.compare(priorityScore.get(b.placeId()), priorityScore.get(a.placeId()));
            if (c != 0) return c;
            c = Integer.compare(likeCount.get(b.placeId()), likeCount.get(a.placeId()));
            if (c != 0) return c;
            c = Double.compare(distKm.get(a.placeId()), distKm.get(b.placeId()));
            if (c != 0) return c;
            return Long.compare(a.placeId(), b.placeId());
        };
    }
}