package com.sync.algorithm.step2;

import com.sync.algorithm.PlaceCategory;
import com.sync.algorithm.step1.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KMeansClusteringTest {

    // ── 헬퍼 ────────────────────────────────────────────────────────────

    private static final double SEOUL_LAT = 37.5665;
    private static final double SEOUL_LNG = 126.9780;
    private static final double BUSAN_LAT = 35.1028;
    private static final double BUSAN_LNG = 129.0403;

    private PlaceInfo place(long id, PlaceCategory cat, int densityPt, double lat, double lng) {
        return new PlaceInfo(id, 1L, "P" + id, cat, densityPt, 60, lat, lng);
    }

    /** priorityScore를 기반으로 voteScore 역산, distanceKm은 직접 지정 */
    private MainPoolPlace mp(long id, double priority, int likeCount, double distKm) {
        return new MainPoolPlace(id, priority, priority * 0.7, likeCount, 0, distKm, false);
    }

    private Step1Meta meta(int K, int densityLimit, int foodPerDay) {
        return new Step1Meta(K, 4, 2, densityLimit, foodPerDay, 10.0, SEOUL_LAT, SEOUL_LNG);
    }

    private Step2Result run(List<MainPoolPlace> pool, List<PlaceInfo> places,
                             int K, int densityLimit, int foodPerDay) {
        Step1Meta m = meta(K, densityLimit, foodPerDay);
        Step1Result step1 = new Step1Result(pool, List.of(), m);
        return KMeansClustering.cluster(new Step2Input(step1, places));
    }

    // ── 방어 코드 ────────────────────────────────────────────────────────

    @Test
    void K가_0이면_빈_결과_반환() {
        List<PlaceInfo> places = List.of(place(1, PlaceCategory.CULTURE, 1, SEOUL_LAT, SEOUL_LNG));
        List<MainPoolPlace> pool = List.of(mp(1, 1.0, 3, 1.0));
        Step1Result step1 = new Step1Result(pool, List.of(), meta(0, 5, 1));

        Step2Result result = KMeansClustering.cluster(new Step2Input(step1, places));

        assertThat(result.dayGroups()).isEmpty();
        assertThat(result.overflow()).isEmpty();
    }

    @Test
    void places에_없는_placeId가_mainPool에_있으면_IllegalArgumentException() {
        List<PlaceInfo> places = List.of(place(1, PlaceCategory.CULTURE, 1, SEOUL_LAT, SEOUL_LNG));
        List<MainPoolPlace> pool = List.of(
                mp(1, 1.0, 3, 1.0),
                mp(99, 0.8, 2, 2.0) // placeId=99은 places에 없음
        );
        Step1Result step1 = new Step1Result(pool, List.of(), meta(1, 5, 1));

        assertThatThrownBy(() -> KMeansClustering.cluster(new Step2Input(step1, places)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    @Test
    void places에_중복_placeId가_있어도_정상_동작() {
        PlaceInfo p1a = place(1, PlaceCategory.CULTURE, 1, SEOUL_LAT, SEOUL_LNG);
        PlaceInfo p1b = place(1, PlaceCategory.NATURE,  2, SEOUL_LAT + 0.1, SEOUL_LNG + 0.1);
        List<PlaceInfo> places = List.of(p1a, p1b); // 동일 placeId=1, 내용 다름
        List<MainPoolPlace> pool = List.of(mp(1, 1.0, 3, 1.0));
        Step1Result step1 = new Step1Result(pool, List.of(), meta(1, 5, 1));

        Step2Result result = KMeansClustering.cluster(new Step2Input(step1, places));

        assertThat(result.dayGroups()).hasSize(1);
        // 첫 번째 PlaceInfo(CULTURE) 사용
        assertThat(result.dayGroups().get(0).places().get(0).category())
                .isEqualTo(PlaceCategory.CULTURE);
    }

    // ── mainPool 비어있음 ────────────────────────────────────────────────

    @Test
    void mainPool_비어있으면_K개의_빈_DayGroup_반환() {
        Step1Meta m = meta(3, 5, 1);
        Step1Result step1 = new Step1Result(List.of(), List.of(), m);
        Step2Result result = KMeansClustering.cluster(new Step2Input(step1, List.of()));

        assertThat(result.dayGroups()).hasSize(3);
        assertThat(result.dayGroups()).allMatch(dg -> dg.places().isEmpty());
        assertThat(result.overflow()).isEmpty();
    }

    // ── K=1 단일 일차 ────────────────────────────────────────────────────

    @Test
    void K1_모든_장소가_day1에_배정됨() {
        List<PlaceInfo> places = List.of(
                place(1, PlaceCategory.CULTURE, 1, SEOUL_LAT,       SEOUL_LNG),
                place(2, PlaceCategory.NATURE,  1, SEOUL_LAT + 0.1, SEOUL_LNG + 0.1),
                place(3, PlaceCategory.FOOD,    1, SEOUL_LAT - 0.1, SEOUL_LNG - 0.1)
        );
        List<MainPoolPlace> pool = List.of(
                mp(1, 1.0, 4, 1.0),
                mp(2, 0.8, 3, 2.0),
                mp(3, 0.6, 3, 3.0)
        );

        Step2Result result = run(pool, places, 1, 5, 1);

        assertThat(result.dayGroups()).hasSize(1);
        DayGroup day1 = result.dayGroups().get(0);
        assertThat(day1.day()).isEqualTo(1);
        assertThat(day1.places()).hasSize(3);
        assertThat(result.overflow()).isEmpty();
    }

    // ── 지리적 클러스터링 (K=2) ─────────────────────────────────────────

    @Test
    void K2_서울과_부산_장소가_별개_일차로_분리됨() {
        // P1, P2 = 서울 / P3, P4 = 부산
        // K-Means++ 초기화: centroid-0 = P1(최고우선), centroid-1 = P3(가장 먼 곳)
        // → day1={P1,P2}, day2={P3,P4}
        List<PlaceInfo> places = List.of(
                place(1, PlaceCategory.CULTURE, 1, SEOUL_LAT,       SEOUL_LNG),
                place(2, PlaceCategory.NATURE,  1, SEOUL_LAT + 0.01, SEOUL_LNG + 0.01),
                place(3, PlaceCategory.CULTURE, 1, BUSAN_LAT,       BUSAN_LNG),
                place(4, PlaceCategory.NATURE,  1, BUSAN_LAT + 0.01, BUSAN_LNG + 0.01)
        );
        List<MainPoolPlace> pool = List.of(
                mp(1, 1.0, 4, 1.0),
                mp(2, 0.8, 3, 2.0),
                mp(3, 0.6, 3, 300.0),
                mp(4, 0.4, 2, 301.0)
        );

        Step2Result result = run(pool, places, 2, 5, 1);

        assertThat(result.dayGroups()).hasSize(2);
        Set<Long> day1Ids = result.dayGroups().get(0).places().stream()
                .map(AssignedPlace::placeId).collect(Collectors.toSet());
        Set<Long> day2Ids = result.dayGroups().get(1).places().stream()
                .map(AssignedPlace::placeId).collect(Collectors.toSet());

        assertThat(day1Ids).containsExactlyInAnyOrder(1L, 2L);
        assertThat(day2Ids).containsExactlyInAnyOrder(3L, 4L);
        assertThat(result.overflow()).isEmpty();
    }

    @Test
    void K2_일차_번호가_1부터_순서대로_부여됨() {
        List<PlaceInfo> places = List.of(
                place(1, PlaceCategory.CULTURE, 1, SEOUL_LAT, SEOUL_LNG),
                place(2, PlaceCategory.CULTURE, 1, BUSAN_LAT, BUSAN_LNG)
        );
        List<MainPoolPlace> pool = List.of(
                mp(1, 1.0, 4, 1.0),
                mp(2, 0.6, 3, 300.0)
        );

        Step2Result result = run(pool, places, 2, 5, 1);

        assertThat(result.dayGroups().get(0).day()).isEqualTo(1);
        assertThat(result.dayGroups().get(1).day()).isEqualTo(2);
    }

    // ── AssignedPlace 필드 검증 ──────────────────────────────────────────

    @Test
    void AssignedPlace에_장소_메타_정보가_올바르게_복사됨() {
        List<PlaceInfo> places = List.of(
                place(1, PlaceCategory.ACTIVITY, 2, 37.5, 127.0)
        );
        List<MainPoolPlace> pool = List.of(
                mp(1, 0.9, 3, 1.5)
        );

        Step2Result result = run(pool, places, 1, 5, 1);

        AssignedPlace ap = result.dayGroups().get(0).places().get(0);
        assertThat(ap.placeId()).isEqualTo(1L);
        assertThat(ap.day()).isEqualTo(1);
        assertThat(ap.category()).isEqualTo(PlaceCategory.ACTIVITY);
        assertThat(ap.latitude()).isEqualTo(37.5);
        assertThat(ap.longitude()).isEqualTo(127.0);
        assertThat(ap.estimatedDuration()).isEqualTo(60);
        assertThat(ap.priorityScore()).isEqualTo(0.9);
    }

    // ── 일별 FOOD 쿼터 제약 ──────────────────────────────────────────────

    @Test
    void 일별_FOOD_쿼터_초과시_우선순위_낮은_FOOD가_overflow() {
        // K=1, foodPerDay=1 → 음식 1개만 허용
        // P1(음식, 높음) → day1 / P2(음식, 낮음) → overflow
        List<PlaceInfo> places = List.of(
                place(1, PlaceCategory.FOOD, 1, SEOUL_LAT, SEOUL_LNG),
                place(2, PlaceCategory.FOOD, 1, SEOUL_LAT, SEOUL_LNG)
        );
        List<MainPoolPlace> pool = List.of(
                mp(1, 1.0, 4, 1.0),
                mp(2, 0.5, 4, 1.0)
        );

        Step2Result result = run(pool, places, 1, 5, 1);

        DayGroup day1 = result.dayGroups().get(0);
        assertThat(day1.places()).hasSize(1);
        assertThat(day1.places().get(0).placeId()).isEqualTo(1L);
        assertThat(result.overflow()).hasSize(1);
        assertThat(result.overflow().get(0).placeId()).isEqualTo(2L);
    }

    @Test
    void FOOD_쿼터는_비FOOD_밀도_예산에_영향_없음() {
        // K=1, foodPerDay=1, densityLimit=3
        // P1(음식, densityPoint=1) + P2(비음식, densityPoint=3) → 둘 다 들어가야 함
        // 음식은 density 예산 소모 안 하므로 P2 통과
        List<PlaceInfo> places = List.of(
                place(1, PlaceCategory.FOOD,    1, SEOUL_LAT, SEOUL_LNG),
                place(2, PlaceCategory.CULTURE, 3, SEOUL_LAT, SEOUL_LNG)
        );
        List<MainPoolPlace> pool = List.of(
                mp(1, 1.0, 4, 1.0),
                mp(2, 0.8, 3, 1.0)
        );

        Step2Result result = run(pool, places, 1, 3, 1);

        assertThat(result.dayGroups().get(0).places()).hasSize(2);
        assertThat(result.overflow()).isEmpty();
    }

    // ── 일별 비FOOD Density 제약 ────────────────────────────────────────

    @Test
    void 일별_density_초과시_우선순위_낮은_장소가_overflow() {
        // K=1, densityLimit=3
        // P1(density=2): 누적=2 ≤ 3 → day1
        // P2(density=2): 2+2=4 > 3 → overflow
        List<PlaceInfo> places = List.of(
                place(1, PlaceCategory.CULTURE, 2, SEOUL_LAT, SEOUL_LNG),
                place(2, PlaceCategory.NATURE,  2, SEOUL_LAT, SEOUL_LNG)
        );
        List<MainPoolPlace> pool = List.of(
                mp(1, 1.0, 4, 1.0),
                mp(2, 0.6, 3, 1.0)
        );

        Step2Result result = run(pool, places, 1, 3, 1);

        DayGroup day1 = result.dayGroups().get(0);
        assertThat(day1.places()).hasSize(1);
        assertThat(day1.places().get(0).placeId()).isEqualTo(1L);
        assertThat(result.overflow()).hasSize(1);
        assertThat(result.overflow().get(0).placeId()).isEqualTo(2L);
    }

    @Test
    void density_정확히_한도와_같으면_overflow_아님() {
        // K=1, densityLimit=4
        // P1(density=2) + P2(density=2) = 4 = 한도 → 둘 다 통과
        List<PlaceInfo> places = List.of(
                place(1, PlaceCategory.CULTURE, 2, SEOUL_LAT, SEOUL_LNG),
                place(2, PlaceCategory.NATURE,  2, SEOUL_LAT, SEOUL_LNG)
        );
        List<MainPoolPlace> pool = List.of(
                mp(1, 1.0, 4, 1.0),
                mp(2, 0.8, 3, 1.0)
        );

        Step2Result result = run(pool, places, 1, 4, 1);

        assertThat(result.dayGroups().get(0).places()).hasSize(2);
        assertThat(result.overflow()).isEmpty();
    }

    // ── K > 장소 수 ──────────────────────────────────────────────────────

    @Test
    void K가_장소수보다_많으면_일부_일차가_비어있음() {
        // K=3, pool=2개 → 1개 일차는 반드시 비어있음
        List<PlaceInfo> places = List.of(
                place(1, PlaceCategory.CULTURE, 1, SEOUL_LAT, SEOUL_LNG),
                place(2, PlaceCategory.NATURE,  1, SEOUL_LAT, SEOUL_LNG)
        );
        List<MainPoolPlace> pool = List.of(
                mp(1, 1.0, 4, 1.0),
                mp(2, 0.8, 3, 1.0)
        );

        Step2Result result = run(pool, places, 3, 5, 1);

        assertThat(result.dayGroups()).hasSize(3);
        long totalAssigned = result.dayGroups().stream()
                .mapToLong(dg -> dg.places().size()).sum();
        assertThat(totalAssigned).isEqualTo(2);
        assertThat(result.overflow()).isEmpty();
        long emptyDays = result.dayGroups().stream()
                .filter(dg -> dg.places().isEmpty()).count();
        assertThat(emptyDays).isGreaterThanOrEqualTo(1);
    }

    @Test
    void K1이면_DayGroup이_1개() {
        List<PlaceInfo> places = List.of(place(1, PlaceCategory.CULTURE, 1, SEOUL_LAT, SEOUL_LNG));
        List<MainPoolPlace> pool = List.of(mp(1, 1.0, 3, 1.0));

        Step2Result result = run(pool, places, 1, 5, 1);

        assertThat(result.dayGroups()).hasSize(1);
    }

    // ── §2-5 density rebalancing + §2-7 load balancing ─────────────────

    @Test
    void 동일좌표_쏠림_발생시_density_리밸런싱으로_인접day에_분배됨() {
        // 4개 장소 모두 동일 좌표 → K-Means가 cluster-0에 전부 몰림
        // densityLimit=2 → §2-5에서 P4(0.4)·P3(0.6) 가 day2로 이동 (haversine=0 <= allowDist)
        // day2로 이동 후 density 한도 도달 → BREAK
        List<PlaceInfo> places = List.of(
                place(1, PlaceCategory.CULTURE, 1, SEOUL_LAT, SEOUL_LNG),
                place(2, PlaceCategory.NATURE,  1, SEOUL_LAT, SEOUL_LNG),
                place(3, PlaceCategory.ACTIVITY,1, SEOUL_LAT, SEOUL_LNG),
                place(4, PlaceCategory.ETC,     1, SEOUL_LAT, SEOUL_LNG)
        );
        List<MainPoolPlace> pool = List.of(
                mp(1, 1.0, 4, 1.0),
                mp(2, 0.8, 3, 1.0),
                mp(3, 0.6, 3, 1.0),
                mp(4, 0.4, 2, 1.0)
        );

        Step2Result result = run(pool, places, 2, 2, 1); // densityLimit=2

        assertThat(result.dayGroups()).hasSize(2);
        assertThat(result.dayGroups().get(0).places()).hasSize(2); // P1, P2
        assertThat(result.dayGroups().get(1).places()).hasSize(2); // P3, P4 (backfill)
        assertThat(result.overflow()).isEmpty();
    }

    @Test
    void density_초과시_낮은_우선순위_장소가_먼저_인접day로_이동() {
        // K=2, densityLimit=1, 3개 장소 모두 동일 좌표
        // §2-5: priority ASC 순서로 처리
        //   P3(0.6) → day2 이동 (haversine=0 <= allowDist, otherDensity=0+1=1 <= 1)
        //   P2(0.8) → day2 density already=1, 이동 불가 → altPool
        //   P1(1.0) → day1 density=1 <= 1, 남음
        List<PlaceInfo> places = List.of(
                place(1, PlaceCategory.CULTURE, 1, SEOUL_LAT, SEOUL_LNG),
                place(2, PlaceCategory.NATURE,  1, SEOUL_LAT, SEOUL_LNG),
                place(3, PlaceCategory.ACTIVITY,1, SEOUL_LAT, SEOUL_LNG)
        );
        List<MainPoolPlace> pool = List.of(
                mp(1, 1.0, 4, 1.0),
                mp(2, 0.8, 3, 1.0),
                mp(3, 0.6, 3, 1.0)
        );

        Step2Result result = run(pool, places, 2, 1, 1); // densityLimit=1

        assertThat(result.dayGroups().get(0).places()).extracting(p -> p.placeId())
                .containsExactly(1L);
        assertThat(result.dayGroups().get(1).places()).extracting(p -> p.placeId())
                .containsExactly(3L); // P3 먼저 이동 (priority 낮은 순)
        assertThat(result.overflow()).hasSize(1);
        assertThat(result.overflow().get(0).placeId()).isEqualTo(2L); // P2 altPool
    }

    @Test
    void FOOD_쿼터_초과분은_altPool로_빈_클러스터는_그대로_남음() {
        // FIX-24: 빈 클러스터는 사용자 수동편집으로 보충 (자동 backfill X)
        // K=2, foodPerDay=1, 모두 동일 좌표
        // §2-4: P1 day1 (quota=1), P2 쿼터 초과 → altPool
        // day2는 빈 클러스터로 유지 (FIX-24)
        List<PlaceInfo> places = List.of(
                place(1, PlaceCategory.FOOD, 1, SEOUL_LAT, SEOUL_LNG),
                place(2, PlaceCategory.FOOD, 1, SEOUL_LAT, SEOUL_LNG)
        );
        List<MainPoolPlace> pool = List.of(
                mp(1, 1.0, 4, 1.0),
                mp(2, 0.8, 3, 1.0)
        );

        Step2Result result = run(pool, places, 2, 5, 1); // foodPerDay=1

        assertThat(result.dayGroups().get(0).places()).hasSize(1); // P1
        assertThat(result.dayGroups().get(1).places()).isEmpty();  // 빈 클러스터 그대로 (FIX-24)
        assertThat(result.overflow()).hasSize(1);
        assertThat(result.overflow().get(0).placeId()).isEqualTo(2L);
    }

    @Test
    void FOOD_쿼터_초과는_altPool에_누적됨() {
        // FIX-24: 빈 클러스터 그대로, 초과 FOOD는 모두 altPool
        // K=2, foodPerDay=1, 3개 FOOD 장소
        // §2-4: P1 day1 (quota=1), P2·P3 쿼터 초과 → altPool
        List<PlaceInfo> places = List.of(
                place(1, PlaceCategory.FOOD, 1, SEOUL_LAT, SEOUL_LNG),
                place(2, PlaceCategory.FOOD, 1, SEOUL_LAT, SEOUL_LNG),
                place(3, PlaceCategory.FOOD, 1, SEOUL_LAT, SEOUL_LNG)
        );
        List<MainPoolPlace> pool = List.of(
                mp(1, 1.0, 4, 1.0),
                mp(2, 0.8, 3, 1.0),
                mp(3, 0.6, 3, 1.0)
        );

        Step2Result result = run(pool, places, 2, 5, 1);

        assertThat(result.dayGroups().get(0).places()).hasSize(1); // P1
        assertThat(result.dayGroups().get(1).places()).isEmpty();  // 빈 클러스터 그대로 (FIX-24)
        assertThat(result.overflow()).hasSize(2);
        assertThat(result.overflow()).extracting(p -> p.placeId())
                .containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void 이미_채워진_일차에는_backfill_하지_않음() {
        // K=2, 서울/부산 분리 → 양쪽 모두 배정됨, overflow 없음
        // backfill이 기존 배정을 건드리지 않는지 확인
        List<PlaceInfo> places = List.of(
                place(1, PlaceCategory.CULTURE, 1, SEOUL_LAT,       SEOUL_LNG),
                place(2, PlaceCategory.NATURE,  1, SEOUL_LAT + 0.01, SEOUL_LNG + 0.01),
                place(3, PlaceCategory.CULTURE, 1, BUSAN_LAT,       BUSAN_LNG),
                place(4, PlaceCategory.NATURE,  1, BUSAN_LAT + 0.01, BUSAN_LNG + 0.01)
        );
        List<MainPoolPlace> pool = List.of(
                mp(1, 1.0, 4, 1.0),
                mp(2, 0.8, 3, 2.0),
                mp(3, 0.6, 3, 300.0),
                mp(4, 0.4, 2, 301.0)
        );

        Step2Result result = run(pool, places, 2, 5, 1);

        // 양쪽 모두 비어있지 않음 (backfill 대상 없음)
        assertThat(result.dayGroups()).allMatch(dg -> !dg.places().isEmpty());
        assertThat(result.overflow()).isEmpty();
    }

    // ── 결정론성 ─────────────────────────────────────────────────────────

    @Test
    void 결정론성_같은_입력이면_항상_같은_출력() {
        List<PlaceInfo> places = List.of(
                place(1, PlaceCategory.CULTURE, 1, SEOUL_LAT,       SEOUL_LNG),
                place(2, PlaceCategory.NATURE,  1, SEOUL_LAT + 0.2, SEOUL_LNG + 0.1),
                place(3, PlaceCategory.FOOD,    1, BUSAN_LAT,       BUSAN_LNG),
                place(4, PlaceCategory.ACTIVITY,1, BUSAN_LAT - 0.1, BUSAN_LNG + 0.2)
        );
        List<MainPoolPlace> pool = List.of(
                mp(1, 1.0, 4, 1.0),
                mp(2, 0.8, 3, 5.0),
                mp(3, 0.6, 3, 300.0),
                mp(4, 0.4, 2, 310.0)
        );

        Step2Result r1 = run(pool, places, 2, 5, 1);
        Step2Result r2 = run(pool, places, 2, 5, 1);

        assertThat(r1.dayGroups()).isEqualTo(r2.dayGroups());
        assertThat(r1.overflow()).isEqualTo(r2.overflow());
    }

    // ── 이상치 후보 플래그 전달 ──────────────────────────────────────────

    @Test
    void isOutlierCandidate_플래그가_AssignedPlace에_전달됨() {
        List<PlaceInfo> places = List.of(
                place(1, PlaceCategory.CULTURE, 1, SEOUL_LAT, SEOUL_LNG),
                place(2, PlaceCategory.NATURE,  1, SEOUL_LAT + 5.0, SEOUL_LNG + 5.0) // 먼 곳
        );
        List<MainPoolPlace> poolWithOutlier = List.of(
                new MainPoolPlace(1, 1.0, 0.7, 3, 0, 1.0,  false),
                new MainPoolPlace(2, 0.5, 0.35, 2, 0, 35.0, true)  // isOutlierCandidate=true
        );

        Step1Meta m = meta(1, 5, 1);
        Step1Result step1 = new Step1Result(poolWithOutlier, List.of(), m);
        Step2Result result = KMeansClustering.cluster(new Step2Input(step1, places));

        List<AssignedPlace> day1 = result.dayGroups().get(0).places();
        AssignedPlace p2 = day1.stream().filter(ap -> ap.placeId() == 2L).findFirst().orElseThrow();
        assertThat(p2.isOutlierCandidate()).isTrue();
    }

    // ── 배정 우선순위 순서 ───────────────────────────────────────────────

    @Test
    void 클러스터_내_장소는_우선순위_내림차순으로_배정됨() {
        // 같은 위치 3개 장소 → 같은 클러스터, priority 순서로 배정
        List<PlaceInfo> places = List.of(
                place(1, PlaceCategory.CULTURE, 1, SEOUL_LAT, SEOUL_LNG),
                place(2, PlaceCategory.NATURE,  1, SEOUL_LAT, SEOUL_LNG),
                place(3, PlaceCategory.ACTIVITY,1, SEOUL_LAT, SEOUL_LNG)
        );
        List<MainPoolPlace> pool = List.of(
                mp(1, 1.0, 4, 1.0),  // 최고 우선순위
                mp(2, 0.7, 3, 1.0),
                mp(3, 0.4, 2, 1.0)   // 최저 우선순위
        );

        Step2Result result = run(pool, places, 1, 5, 1);

        List<AssignedPlace> places1 = result.dayGroups().get(0).places();
        assertThat(places1.get(0).placeId()).isEqualTo(1L);
        assertThat(places1.get(1).placeId()).isEqualTo(2L);
        assertThat(places1.get(2).placeId()).isEqualTo(3L);
    }
}
