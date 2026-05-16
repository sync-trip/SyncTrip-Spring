package com.sync.algorithm.step2;

import com.sync.algorithm.AlgorithmConstants;
import com.sync.algorithm.PlaceCategory;
import com.sync.algorithm.step1.MainPoolPlace;
import com.sync.algorithm.step1.PlaceInfo;
import com.sync.algorithm.step1.Step1Meta;
import com.sync.algorithm.step1.Step1Result;

import java.util.*;
import java.util.stream.Collectors;

public final class KMeansClustering {

    private KMeansClustering() {}

    /**
     * mainPool 장소를 K일에 지리적으로 분배한다.
     *
     * 설계 제약:
     * - 순수 함수 — DB 접근 없음, 입력 → 출력만
     * - 결정론적 — 같은 입력이면 항상 같은 출력
     * - 최초 1회만 실행 (재실행 금지, 이후 수정은 Step3 TSP만)
     */
    public static Step2Result cluster(Step2Input input) {
        Step1Result step1 = input.step1Result();
        Step1Meta meta = step1.meta();
        int K = meta.K();
        List<MainPoolPlace> mainPool = step1.mainPool();

        // K=0 또는 음수: 날짜 역전 입력 등 비정상 상태 방어
        if (K <= 0) {
            return new Step2Result(List.of(), List.of());
        }

        // (a, b) -> a: 중복 placeId는 첫 번째 항목 사용 (IllegalStateException 방지)
        Map<Long, PlaceInfo> placeById = input.places().stream()
                .collect(Collectors.toMap(PlaceInfo::placeId, p -> p, (a, b) -> a));

        if (mainPool.isEmpty()) {
            return new Step2Result(buildEmptyDays(K), List.of());
        }

        // K가 장소 수보다 많으면 의미 있는 클러스터 수를 장소 수로 제한
        int effectiveK = Math.min(K, mainPool.size());

        // ── 1. 초기 센트로이드 (결정론적 K-Means++) ──────────────────────
        // 첫 번째 센트로이드: 우선순위 최고 장소 (mainPool[0])
        // 이후 센트로이드: 기존 센트로이드에서 가장 먼 장소 순서로 선택
        double[][] centroids = initCentroids(mainPool, placeById, effectiveK);

        // ── 2. K-Means 수렴까지 반복 (최대 KMEANS_MAX_ITER회) ──────────
        int[] assignments = assignAll(mainPool, placeById, centroids);
        for (int iter = 0; iter < AlgorithmConstants.KMEANS_MAX_ITER - 1; iter++) {
            double[][] updated = updateCentroids(mainPool, placeById, assignments, effectiveK, centroids);
            int[] newAssign = assignAll(mainPool, placeById, updated);
            if (Arrays.equals(assignments, newAssign)) {
                centroids = updated;
                break;
            }
            assignments = newAssign;
            centroids = updated;
        }

        // ── 3. 일별 제약 적용 후 결과 구성 ─────────────────────────────
        return buildResult(mainPool, placeById, assignments, effectiveK, K, meta);
    }

    // ── 초기 센트로이드 선택 ─────────────────────────────────────────────

    private static double[][] initCentroids(List<MainPoolPlace> mainPool,
                                             Map<Long, PlaceInfo> placeById,
                                             int effectiveK) {
        double[][] centroids = new double[effectiveK][2];
        boolean[] used = new boolean[mainPool.size()];

        PlaceInfo first = lookup(placeById, mainPool.get(0).placeId());
        centroids[0] = new double[]{first.latitude(), first.longitude()};
        used[0] = true;

        for (int k = 1; k < effectiveK; k++) {
            double maxMinDist = -1.0;
            int bestIdx = -1;
            for (int i = 0; i < mainPool.size(); i++) {
                if (used[i]) continue;
                PlaceInfo pi = lookup(placeById, mainPool.get(i).placeId());
                double minDist = minDistToCentroids(pi.latitude(), pi.longitude(), centroids, k);
                if (minDist > maxMinDist) {
                    maxMinDist = minDist;
                    bestIdx = i;
                }
            }
            PlaceInfo best = lookup(placeById, mainPool.get(bestIdx).placeId());
            centroids[k] = new double[]{best.latitude(), best.longitude()};
            used[bestIdx] = true;
        }
        return centroids;
    }

    // ── 할당 단계: 각 장소를 가장 가까운 센트로이드 클러스터에 배정 ────

    private static int[] assignAll(List<MainPoolPlace> mainPool,
                                    Map<Long, PlaceInfo> placeById,
                                    double[][] centroids) {
        int[] assignments = new int[mainPool.size()];
        for (int i = 0; i < mainPool.size(); i++) {
            PlaceInfo pi = lookup(placeById, mainPool.get(i).placeId());
            double minDist = Double.MAX_VALUE;
            int best = 0;
            for (int k = 0; k < centroids.length; k++) {
                double d = euclidean(pi.latitude(), pi.longitude(),
                        centroids[k][0], centroids[k][1]);
                if (d < minDist) {
                    minDist = d;
                    best = k;
                }
            }
            assignments[i] = best;
        }
        return assignments;
    }

    // ── 업데이트 단계: 각 클러스터의 평균 좌표로 센트로이드 재계산 ─────

    private static double[][] updateCentroids(List<MainPoolPlace> mainPool,
                                               Map<Long, PlaceInfo> placeById,
                                               int[] assignments,
                                               int effectiveK,
                                               double[][] prevCentroids) {
        double[][] sums = new double[effectiveK][2];
        int[] counts = new int[effectiveK];

        for (int i = 0; i < mainPool.size(); i++) {
            PlaceInfo pi = lookup(placeById, mainPool.get(i).placeId());
            int k = assignments[i];
            sums[k][0] += pi.latitude();
            sums[k][1] += pi.longitude();
            counts[k]++;
        }

        double[][] updated = new double[effectiveK][2];
        for (int k = 0; k < effectiveK; k++) {
            if (counts[k] == 0) {
                // 빈 클러스터는 이전 센트로이드 유지 (안정성 보장)
                updated[k] = prevCentroids[k].clone();
            } else {
                updated[k][0] = sums[k][0] / counts[k];
                updated[k][1] = sums[k][1] / counts[k];
            }
        }
        return updated;
    }

    // ── Phase 1: 일별 제약 적용 및 결과 구성 ─────────────────────────────
    //
    // 클러스터 내 장소는 Step1에서 정렬된 순서(priority DESC → likeCount DESC
    // → distanceKm ASC → placeId ASC)를 유지한다.
    // 우선순위 높은 장소부터 제약을 충족하는 한 배정하고 나머지는 overflow로.
    //
    // Phase 2: overflow → 빈 일차 backfill
    //
    // 동일 좌표 쏠림(모든 장소가 cluster-0에 몰리는 현상) 등으로
    // overflow + 빈 일차가 공존할 때 overflow를 우선순위 순서로 빈 일차에 재배정한다.

    private static Step2Result buildResult(List<MainPoolPlace> mainPool,
                                            Map<Long, PlaceInfo> placeById,
                                            int[] assignments,
                                            int effectiveK,
                                            int K,
                                            Step1Meta meta) {
        // 클러스터별 장소 그룹화 (mainPool 순서 유지)
        Map<Integer, List<MainPoolPlace>> clusters = new LinkedHashMap<>();
        for (int k = 0; k < effectiveK; k++) clusters.put(k, new ArrayList<>());
        for (int i = 0; i < mainPool.size(); i++) {
            clusters.get(assignments[i]).add(mainPool.get(i));
        }

        List<DayGroup> dayGroups = new ArrayList<>(K);
        List<MainPoolPlace> overflow = new ArrayList<>();

        // Phase 1: K-Means 클러스터 기반 초기 배정
        for (int dayIdx = 0; dayIdx < K; dayIdx++) {
            int day = dayIdx + 1;
            if (dayIdx >= effectiveK) {
                dayGroups.add(new DayGroup(day, List.of()));
                continue;
            }

            List<MainPoolPlace> cluster = clusters.get(dayIdx);
            List<AssignedPlace> dayPlaces = new ArrayList<>();
            int foodCount = 0;
            int nonFoodDensity = 0;

            for (MainPoolPlace p : cluster) {
                PlaceInfo pi = lookup(placeById, p.placeId());
                if (pi.category() == PlaceCategory.FOOD) {
                    if (foodCount >= meta.foodPerDayQuota()) {
                        overflow.add(p);
                    } else {
                        dayPlaces.add(toAssigned(p, pi, day));
                        foodCount++;
                    }
                } else {
                    if (nonFoodDensity + pi.densityPoint() > meta.densityLimit()) {
                        overflow.add(p);
                    } else {
                        dayPlaces.add(toAssigned(p, pi, day));
                        nonFoodDensity += pi.densityPoint();
                    }
                }
            }

            dayGroups.add(new DayGroup(day, Collections.unmodifiableList(dayPlaces)));
        }

        // Phase 2: overflow → 빈 일차 backfill
        backfillEmptyDays(dayGroups, overflow, placeById, meta);

        return new Step2Result(
                Collections.unmodifiableList(dayGroups),
                Collections.unmodifiableList(overflow)
        );
    }

    // ── Phase 2 backfill ──────────────────────────────────────────────────
    //
    // overflow를 우선순위 순서(priority DESC → likeCount DESC → distKm ASC → placeId ASC)로
    // 정렬한 뒤, 비어있는 일차에 일별 제약(FOOD 쿼터 + density)을 지키며 채운다.
    // 한 일차를 채우고 나면 남은 overflow로 다음 빈 일차를 채운다.

    private static void backfillEmptyDays(List<DayGroup> dayGroups,
                                           List<MainPoolPlace> overflow,
                                           Map<Long, PlaceInfo> placeById,
                                           Step1Meta meta) {
        if (overflow.isEmpty()) return;

        overflow.sort((a, b) -> {
            int c = Double.compare(b.priorityScore(), a.priorityScore());
            if (c != 0) return c;
            c = Integer.compare(b.likeCount(), a.likeCount());
            if (c != 0) return c;
            c = Double.compare(a.distanceKm(), b.distanceKm());
            if (c != 0) return c;
            return Long.compare(a.placeId(), b.placeId());
        });

        for (int i = 0; i < dayGroups.size(); i++) {
            if (!dayGroups.get(i).places().isEmpty() || overflow.isEmpty()) continue;

            int day = dayGroups.get(i).day();
            List<AssignedPlace> newPlaces = new ArrayList<>();
            List<MainPoolPlace> remaining = new ArrayList<>();
            int foodCount = 0;
            int nonFoodDensity = 0;

            for (MainPoolPlace p : overflow) {
                PlaceInfo pi = lookup(placeById, p.placeId());
                if (pi.category() == PlaceCategory.FOOD) {
                    if (foodCount >= meta.foodPerDayQuota()) {
                        remaining.add(p);
                    } else {
                        newPlaces.add(toAssigned(p, pi, day));
                        foodCount++;
                    }
                } else {
                    if (nonFoodDensity + pi.densityPoint() > meta.densityLimit()) {
                        remaining.add(p);
                    } else {
                        newPlaces.add(toAssigned(p, pi, day));
                        nonFoodDensity += pi.densityPoint();
                    }
                }
            }

            dayGroups.set(i, new DayGroup(day, Collections.unmodifiableList(newPlaces)));
            overflow.clear();
            overflow.addAll(remaining);
        }
    }

    // ── 유틸 ─────────────────────────────────────────────────────────────

    private static AssignedPlace toAssigned(MainPoolPlace p, PlaceInfo pi, int day) {
        return new AssignedPlace(
                p.placeId(), day,
                pi.category(), pi.latitude(), pi.longitude(),
                pi.estimatedDuration(), p.priorityScore(), p.isOutlierCandidate()
        );
    }

    private static List<DayGroup> buildEmptyDays(int K) {
        List<DayGroup> days = new ArrayList<>(K);
        for (int i = 1; i <= K; i++) {
            days.add(new DayGroup(i, List.of()));
        }
        return Collections.unmodifiableList(days);
    }

    private static double minDistToCentroids(double lat, double lng,
                                              double[][] centroids, int count) {
        double min = Double.MAX_VALUE;
        for (int j = 0; j < count; j++) {
            min = Math.min(min, euclidean(lat, lng, centroids[j][0], centroids[j][1]));
        }
        return min;
    }

    /** placeId로 PlaceInfo를 조회하고, 없으면 즉시 실패 */
    private static PlaceInfo lookup(Map<Long, PlaceInfo> placeById, long placeId) {
        PlaceInfo pi = placeById.get(placeId);
        if (pi == null) {
            throw new IllegalArgumentException("PlaceInfo not found for placeId: " + placeId);
        }
        return pi;
    }

    /** 위도·경도 2D 유클리드 거리 (같은 목적지 내 클러스터링용) */
    private static double euclidean(double lat1, double lng1, double lat2, double lng2) {
        double dLat = lat1 - lat2;
        double dLng = lng1 - lng2;
        return Math.sqrt(dLat * dLat + dLng * dLng);
    }
}
