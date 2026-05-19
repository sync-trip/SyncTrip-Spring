package com.sync.algorithm.planb;

import com.sync.algorithm.AlgorithmConstants;
import com.sync.algorithm.step1.AltPoolPlace;
import com.sync.algorithm.step1.MainPoolPlace;
import com.sync.algorithm.step1.PlaceInfo;
import com.sync.algorithm.step3.DaySchedule;
import com.sync.algorithm.step3.ScheduledPlace;

import java.util.*;
import java.util.stream.Collectors;

public final class PlanBRecommender {

    private PlanBRecommender() {}

    /**
     * targetPlaceId 를 교체할 후보를 altPool + Step2 overflow 에서 추천한다.
     *
     * 점수 = priorityScore × PLANB_VOTE_WEIGHT + geoScore × PLANB_GEO_WEIGHT
     * geoScore = max(0, 1 − distKm / PLANB_MAX_DIST_KM)
     *
     * 설계 제약:
     * - 순수 함수 — DB 접근 없음
     * - 이미 스케줄된 장소는 후보에서 제외
     * - 좌표 없는 후보는 조용히 제외 (데이터 불완전 방어)
     */
    public static PlanBResult recommend(PlanBInput input) {
        Map<Long, PlaceInfo> placeById = input.places().stream()
                .collect(Collectors.toMap(PlaceInfo::placeId, p -> p, (a, b) -> a));

        PlaceInfo target = placeById.get(input.targetPlaceId());
        if (target == null) {
            throw new IllegalArgumentException(
                    "PlaceInfo not found for targetPlaceId: " + input.targetPlaceId());
        }

        // 현재 스케줄에 있는 placeId 집합 (후보에서 제외)
        Set<Long> scheduled = input.step3Result().daySchedules().stream()
                .flatMap(ds -> ds.places().stream())
                .map(ScheduledPlace::placeId)
                .collect(Collectors.toSet());

        List<PlanBCandidate> candidates = new ArrayList<>();

        // ── altPool 후보 ───────────────────────────────────────────────
        for (AltPoolPlace ap : input.altPool()) {
            if (scheduled.contains(ap.placeId())) continue;
            PlaceInfo pi = placeById.get(ap.placeId());
            if (pi == null) continue;
            if (pi.category() != target.category()) continue;
            double distKm = haversine(target.latitude(), target.longitude(),
                    pi.latitude(), pi.longitude());
            if (distKm > AlgorithmConstants.PLANB_MAX_DIST_KM) continue;
            candidates.add(toCandidate(ap.placeId(), ap.priorityScore(), pi, distKm, false));
        }

        // ── overflow 후보 ──────────────────────────────────────────────
        for (MainPoolPlace mp : input.step3Result().overflow()) {
            if (scheduled.contains(mp.placeId())) continue;
            PlaceInfo pi = placeById.get(mp.placeId());
            if (pi == null) continue;
            if (pi.category() != target.category()) continue;
            double distKm = haversine(target.latitude(), target.longitude(),
                    pi.latitude(), pi.longitude());
            if (distKm > AlgorithmConstants.PLANB_MAX_DIST_KM) continue;
            candidates.add(toCandidate(mp.placeId(), mp.priorityScore(), pi, distKm, true));
        }

        // ── 정렬: score DESC → distKm ASC → placeId ASC (결정론성) ───
        candidates.sort((a, b) -> {
            int c = Double.compare(b.recommendScore(), a.recommendScore());
            if (c != 0) return c;
            c = Double.compare(a.distanceKmToTarget(), b.distanceKmToTarget());
            if (c != 0) return c;
            return Long.compare(a.placeId(), b.placeId());
        });

        List<PlanBCandidate> top = candidates.size() <= AlgorithmConstants.MAX_PLANB_RECOMMENDATIONS
                ? Collections.unmodifiableList(candidates)
                : Collections.unmodifiableList(
                        candidates.subList(0, AlgorithmConstants.MAX_PLANB_RECOMMENDATIONS));

        return new PlanBResult(top);
    }

    private static PlanBCandidate toCandidate(long placeId, double priorityScore,
                                               PlaceInfo pi, double distKm,
                                               boolean fromOverflow) {
        double geoScore = Math.max(0.0, 1.0 - distKm / AlgorithmConstants.PLANB_MAX_DIST_KM);
        double score = priorityScore * AlgorithmConstants.PLANB_VOTE_WEIGHT
                + geoScore * AlgorithmConstants.PLANB_GEO_WEIGHT;
        return new PlanBCandidate(placeId, score, pi.category(),
                pi.estimatedDuration(), distKm, fromOverflow);
    }

    private static double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return AlgorithmConstants.EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
