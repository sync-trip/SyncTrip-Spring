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

        // ── 3. §2-4/§2-5/§2-7 제약 적용 후 결과 구성 ──────────────────
        return buildResult(mainPool, placeById, assignments, effectiveK, K, meta, centroids);
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

    // ── §2-4 FOOD 쿼터 + §2-5 Density 리밸런싱 + §2-7 로드밸런싱 ───────
    //
    // §2-4: 클러스터별 FOOD 쿼터 초과분 → altPool
    // §2-5: Density 초과 day → 저우선순위 비FOOD를 인접 day로 이동 or altPool
    //        (allow_dist = maxDistKm × 0.3, 최대 K×2회 반복)
    // §2-6: (폐지) [FIX-24] 빈 클러스터는 그대로
    // §2-7: count 초과 day → 비FOOD를 count 부족 day로 이동
    //        (FIX-44: 거리/density 조건 미충족 시 강제 이동 X)

    private static Step2Result buildResult(List<MainPoolPlace> mainPool,
                                            Map<Long, PlaceInfo> placeById,
                                            int[] assignments,
                                            int effectiveK,
                                            int K,
                                            Step1Meta meta,
                                            double[][] centroids) {
        // 가변 클러스터 구성 (mainPool 순서 = priority DESC 유지)
        Map<Integer, List<MainPoolPlace>> clusters = new LinkedHashMap<>();
        for (int k = 0; k < K; k++) clusters.put(k, new ArrayList<>());
        for (int i = 0; i < mainPool.size(); i++) {
            clusters.get(assignments[i]).add(mainPool.get(i));
        }

        List<MainPoolPlace> altPool = new ArrayList<>();

        // §2-4: FOOD 쿼터 초과분 → altPool
        // mainPool 순서(priority DESC)를 유지하므로 앞에서부터 quota개 FOOD를 우선 보호
        for (int k = 0; k < effectiveK; k++) {
            List<MainPoolPlace> cluster = clusters.get(k);
            int foodCount = 0;
            List<MainPoolPlace> excess = new ArrayList<>();
            for (MainPoolPlace p : cluster) {
                if (lookup(placeById, p.placeId()).category() == PlaceCategory.FOOD) {
                    if (foodCount >= meta.foodPerDayQuota()) excess.add(p);
                    else foodCount++;
                }
            }
            cluster.removeAll(excess);
            altPool.addAll(excess);
        }

        // §2-5: Density 리밸런싱
        altPool.addAll(rebalanceDensity(clusters, centroids, effectiveK, meta, placeById));

        // §2-7: 로드밸런싱 (FIX-44: 조건 미충족 시 강제 이동 X)
        rebalanceLoad(clusters, centroids, effectiveK, meta, placeById);

        // DayGroup 구성 — 빈 클러스터는 그대로 (FIX-24)
        List<DayGroup> dayGroups = new ArrayList<>(K);
        for (int dayIdx = 0; dayIdx < K; dayIdx++) {
            int day = dayIdx + 1;
            List<MainPoolPlace> cluster = clusters.get(dayIdx);
            if (cluster == null || cluster.isEmpty()) {
                dayGroups.add(new DayGroup(day, List.of()));
            } else {
                List<AssignedPlace> dayPlaces = cluster.stream()
                        .map(p -> toAssigned(p, lookup(placeById, p.placeId()), day))
                        .collect(Collectors.toList());
                dayGroups.add(new DayGroup(day, Collections.unmodifiableList(dayPlaces)));
            }
        }

        return new Step2Result(
                Collections.unmodifiableList(dayGroups),
                Collections.unmodifiableList(altPool)
        );
    }

    // ── §2-5 Density 리밸런싱 ────────────────────────────────────────────
    //
    // density 초과 day에서 비FOOD를 우선순위 낮은 순으로 꺼내어:
    //   1) 인접 day (haversine ≤ allow_dist) 중 density 여유 있으면 이동
    //   2) 이동 불가 → altPool 강등
    // 최대 effectiveK×2회 반복하여 수렴 확인.

    private static List<MainPoolPlace> rebalanceDensity(
            Map<Integer, List<MainPoolPlace>> clusters,
            double[][] centroids,
            int effectiveK,
            Step1Meta meta,
            Map<Long, PlaceInfo> placeById) {

        int maxIter = effectiveK * 2;
        double allowDist = meta.maxDistKm() * 0.3;
        List<MainPoolPlace> altPool = new ArrayList<>();

        for (int iter = 0; iter < maxIter; iter++) {
            List<Integer> overDays = new ArrayList<>();
            for (int k = 0; k < effectiveK; k++) {
                if (calcNonFoodDensity(clusters.get(k), placeById) > meta.densityLimit()) {
                    overDays.add(k);
                }
            }
            if (overDays.isEmpty()) break;

            for (int dayK : overDays) {
                List<MainPoolPlace> cluster = clusters.get(dayK);
                // 우선순위 낮은(ASC) 비FOOD부터 이동 시도 (tie-break: placeId ASC → 결정론성)
                List<MainPoolPlace> candidates = cluster.stream()
                        .filter(p -> lookup(placeById, p.placeId()).category() != PlaceCategory.FOOD)
                        .sorted(Comparator.comparingDouble(MainPoolPlace::priorityScore)
                                .thenComparingLong(MainPoolPlace::placeId))
                        .collect(Collectors.toList());

                for (MainPoolPlace place : candidates) {
                    PlaceInfo pi = lookup(placeById, place.placeId());
                    boolean moved = false;

                    // day ASC 순서로 탐색 → 결정론성 보장
                    for (int otherK = 0; otherK < effectiveK; otherK++) {
                        if (otherK == dayK) continue;
                        double dist = haversine(pi.latitude(), pi.longitude(),
                                centroids[otherK][0], centroids[otherK][1]);
                        int otherDensity = calcNonFoodDensity(clusters.get(otherK), placeById);
                        if (dist <= allowDist
                                && otherDensity + pi.densityPoint() <= meta.densityLimit()) {
                            cluster.remove(place);
                            clusters.get(otherK).add(place);
                            moved = true;
                            break;
                        }
                    }

                    if (!moved) {
                        // 인접 day 없음 → altPool 강등
                        cluster.remove(place);
                        altPool.add(place);
                    }

                    // 현재 day density가 한도 이하면 중단
                    if (calcNonFoodDensity(cluster, placeById) <= meta.densityLimit()) break;
                }
            }
        }

        return altPool;
    }

    // ── §2-7 로드밸런싱 ──────────────────────────────────────────────────
    //
    // count > avg_ceil 인 day에서 비FOOD를 count < avg_floor 인 day로 이동.
    // FIX-44: 거리(allow_dist) 또는 density 조건 실패 시 강제 이동하지 않음.

    private static void rebalanceLoad(
            Map<Integer, List<MainPoolPlace>> clusters,
            double[][] centroids,
            int effectiveK,
            Step1Meta meta,
            Map<Long, PlaceInfo> placeById) {

        int totalPlaces = 0;
        for (int k = 0; k < effectiveK; k++) totalPlaces += clusters.get(k).size();
        int avgCeil = (int) Math.ceil((double) totalPlaces / effectiveK);
        int avgFloor = totalPlaces / effectiveK;
        double allowDist = meta.maxDistKm() * 0.3;

        for (int overK = 0; overK < effectiveK; overK++) {
            List<MainPoolPlace> overCluster = clusters.get(overK);
            if (overCluster.size() <= avgCeil) continue;

            List<MainPoolPlace> candidates = overCluster.stream()
                    .filter(p -> lookup(placeById, p.placeId()).category() != PlaceCategory.FOOD)
                    .sorted(Comparator.comparingDouble(MainPoolPlace::priorityScore)
                            .thenComparingLong(MainPoolPlace::placeId))
                    .collect(Collectors.toList());

            for (MainPoolPlace place : candidates) {
                if (overCluster.size() <= avgCeil) break;

                PlaceInfo pi = lookup(placeById, place.placeId());

                for (int underK = 0; underK < effectiveK; underK++) {
                    List<MainPoolPlace> underCluster = clusters.get(underK);
                    if (underCluster.size() >= avgFloor) continue;

                    double dist = haversine(pi.latitude(), pi.longitude(),
                            centroids[underK][0], centroids[underK][1]);
                    int underDensity = calcNonFoodDensity(underCluster, placeById);
                    if (dist <= allowDist
                            && underDensity + pi.densityPoint() <= meta.densityLimit()) {
                        overCluster.remove(place);
                        underCluster.add(place);
                        break;
                    }
                    // FIX-44: 조건 미충족 시 강제 이동 X
                }
            }
        }
    }

    // ── 유틸 ─────────────────────────────────────────────────────────────

    private static int calcNonFoodDensity(List<MainPoolPlace> cluster,
                                           Map<Long, PlaceInfo> placeById) {
        int sum = 0;
        for (MainPoolPlace p : cluster) {
            PlaceInfo pi = lookup(placeById, p.placeId());
            if (pi.category() != PlaceCategory.FOOD) sum += pi.densityPoint();
        }
        return sum;
    }

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

    /** 위도·경도 2D 유클리드 거리 (같은 목적지 내 K-Means 클러스터링용) */
    private static double euclidean(double lat1, double lng1, double lat2, double lng2) {
        double dLat = lat1 - lat2;
        double dLng = lng1 - lng2;
        return Math.sqrt(dLat * dLat + dLng * dLng);
    }

    /** Haversine 공식으로 두 좌표 간 실제 거리(km) 계산 — §2-5/§2-7 rebalancing 전용 */
    private static double haversine(double lat1, double lng1, double lat2, double lng2) {
        double R = AlgorithmConstants.EARTH_RADIUS_KM;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double sinDLat = Math.sin(dLat / 2);
        double sinDLng = Math.sin(dLng / 2);
        double a = sinDLat * sinDLat
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * sinDLng * sinDLng;
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
