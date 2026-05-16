package com.sync.algorithm.step1;

import com.sync.algorithm.PlaceCategory;
import com.sync.algorithm.TravelStyle;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class WeightedCostFunctionTest {

    private static final double DEST_LAT = 37.5665;  // 서울
    private static final double DEST_LNG = 126.9780;

    // ── 헬퍼 ────────────────────────────────────────────────────────────

    private GroupInfo group(TravelStyle style, int days) {
        LocalDate start = LocalDate.of(2025, 7, 1);
        return new GroupInfo(1L, DEST_LAT, DEST_LNG, style, start, start.plusDays(days - 1), false);
    }

    private List<MemberInfo> members(int count) {
        List<MemberInfo> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add(new MemberInfo(i, "MEMBER", true));
        }
        return list;
    }

    private PlaceInfo place(long id, PlaceCategory cat, int density, double lat, double lng) {
        return new PlaceInfo(id, 1L, "Place " + id, cat, density, 60, lat, lng);
    }

    private VoteInfo like(long placeId, long userId)    { return new VoteInfo(placeId, userId,  1); }
    private VoteInfo dislike(long placeId, long userId) { return new VoteInfo(placeId, userId, -1); }

    // ── 기본 투표 집계 & 통과 분류 ──────────────────────────────────────

    @Test
    void 기본_통과_및_탈락_분류() {
        // 4멤버: passed_threshold = ceil(4*0.5) = 2
        // p1: 3 likes → mainPool
        // p2: 1 like  → altPool (alt 범위 [1,1], vote_score = 2.0 > 0)
        // p3: 0 likes → 탈락
        List<PlaceInfo> places = List.of(
                place(1, PlaceCategory.CULTURE, 2, DEST_LAT, DEST_LNG),
                place(2, PlaceCategory.CULTURE, 2, DEST_LAT, DEST_LNG),
                place(3, PlaceCategory.CULTURE, 2, DEST_LAT, DEST_LNG)
        );
        List<VoteInfo> votes = List.of(
                like(1, 1), like(1, 2), like(1, 3),
                like(2, 1),
                dislike(3, 1)
        );
        Step1Result result = WeightedCostFunction.compute(
                new Step1Input(group(TravelStyle.RELAXED, 3), members(4), places, votes));

        assertThat(result.meta().passedThreshold()).isEqualTo(2);
        assertThat(result.mainPool()).extracting(MainPoolPlace::placeId).contains(1L);
        assertThat(result.altPool()).extracting(AltPoolPlace::placeId).contains(2L);
        // p3: 0 likes → 완전 탈락
        assertThat(result.mainPool()).extracting(MainPoolPlace::placeId).doesNotContain(3L);
        assertThat(result.altPool()).extracting(AltPoolPlace::placeId).doesNotContain(3L);
    }

    @Test
    void vote_score_계산_정확성() {
        // 2 likes, 1 dislike → total_voters=3
        // vote_score = (2/3)*2 − (1/3) = 4/3 − 1/3 = 1.0
        PlaceInfo p = place(1, PlaceCategory.CULTURE, 2, DEST_LAT, DEST_LNG);
        List<VoteInfo> votes = List.of(like(1, 1), like(1, 2), dislike(1, 3));
        Step1Result result = WeightedCostFunction.compute(
                new Step1Input(group(TravelStyle.RELAXED, 1), members(4), List.of(p), votes));

        assertThat(result.mainPool()).hasSize(1);
        assertThat(result.mainPool().get(0).voteScore()).isCloseTo(1.0, within(1e-9));
        assertThat(result.mainPool().get(0).likeCount()).isEqualTo(2);
        assertThat(result.mainPool().get(0).dislikeCount()).isEqualTo(1);
    }

    @Test
    void bookmark_자동LIKE_집계() {
        // result=0(BOOKMARK)도 like_count에 포함 [FIX-1]
        PlaceInfo p = place(1, PlaceCategory.CULTURE, 2, DEST_LAT, DEST_LNG);
        List<VoteInfo> votes = List.of(
                new VoteInfo(1L, 1L, 0),  // BOOKMARK → LIKE
                new VoteInfo(1L, 2L, 0)   // BOOKMARK → LIKE
        );
        // 4멤버, threshold=2 → 2 likes → passed
        Step1Result result = WeightedCostFunction.compute(
                new Step1Input(group(TravelStyle.RELAXED, 1), members(4), List.of(p), votes));

        assertThat(result.mainPool()).extracting(MainPoolPlace::placeId).contains(1L);
        assertThat(result.mainPool().get(0).likeCount()).isEqualTo(2);
    }

    // ── [FIX-15] 미투표 처리 ───────────────────────────────────────────

    @Test
    void fix15_미투표_장소는_자동_탈락() {
        // 아무도 투표 안 한 장소 → vote_score=0, like_count=0 < threshold → 탈락
        PlaceInfo p = place(1, PlaceCategory.CULTURE, 2, DEST_LAT, DEST_LNG);
        Step1Result result = WeightedCostFunction.compute(
                new Step1Input(group(TravelStyle.RELAXED, 1), members(4), List.of(p), List.of()));

        assertThat(result.mainPool()).isEmpty();
        assertThat(result.altPool()).isEmpty();
    }

    @Test
    void fix15_미투표자는_분모_제외() {
        // 6멤버 중 3명만 투표, 모두 LIKE
        // like_rate = 3/3 = 1.0 (미투표 2명은 분모 제외)
        // vote_score = 1.0*2 − 0 = 2.0
        PlaceInfo p = place(1, PlaceCategory.CULTURE, 2, DEST_LAT, DEST_LNG);
        List<VoteInfo> votes = List.of(like(1, 1), like(1, 2), like(1, 3));
        // 6멤버, threshold=3 → 3 likes → passed
        Step1Result result = WeightedCostFunction.compute(
                new Step1Input(group(TravelStyle.RELAXED, 1), members(6), List.of(p), votes));

        assertThat(result.mainPool()).hasSize(1);
        assertThat(result.mainPool().get(0).voteScore()).isCloseTo(2.0, within(1e-9));
    }

    // ── [FIX-11] 싫어요 테러 컷오프 ───────────────────────────────────

    @Test
    void fix11_싫어요_압도_장소_altPool_진입_불가() {
        // 6멤버: like=2, dislike=4
        // total_voters=6, like_rate=2/6=1/3, dislike_rate=4/6=2/3
        // vote_score = (1/3)*2 − (2/3) = 0 → NOT > 0 → 탈락
        // passed_threshold=3, like=2 < 3 → 후보 alt 범위 [1,2] ∩ vote_score>0 불충족
        PlaceInfo p = place(1, PlaceCategory.CULTURE, 2, DEST_LAT, DEST_LNG);
        List<VoteInfo> votes = List.of(
                like(1, 1), like(1, 2),
                dislike(1, 3), dislike(1, 4), dislike(1, 5), dislike(1, 6)
        );
        Step1Result result = WeightedCostFunction.compute(
                new Step1Input(group(TravelStyle.RELAXED, 1), members(6), List.of(p), votes));

        assertThat(result.mainPool()).isEmpty();
        assertThat(result.altPool()).isEmpty();
    }

    @Test
    void fix11_싫어요_있어도_vote_score_양수면_altPool_허용() {
        // 6멤버: like=2, dislike=1 → vote_score = (2/3)*2 − (1/3) = 1.0 > 0 → altPool OK
        PlaceInfo p = place(1, PlaceCategory.CULTURE, 2, DEST_LAT, DEST_LNG);
        List<VoteInfo> votes = List.of(
                like(1, 1), like(1, 2),
                dislike(1, 3)
        );
        Step1Result result = WeightedCostFunction.compute(
                new Step1Input(group(TravelStyle.RELAXED, 1), members(6), List.of(p), votes));

        assertThat(result.altPool()).extracting(AltPoolPlace::placeId).contains(1L);
    }

    // ── [FIX-14] 거리 페널티 무시 ─────────────────────────────────────

    @Test
    void fix14_도보권_내_max_dist_3km미만_페널티_없음() {
        // 두 장소 모두 2km 내 → max_dist < 3km → norm_dist = 0
        // priority_score = vote_score * 0.7 (거리 페널티 없음)
        PlaceInfo p1 = place(1, PlaceCategory.CULTURE, 2, DEST_LAT + 0.010, DEST_LNG); // ~1.1km
        PlaceInfo p2 = place(2, PlaceCategory.CULTURE, 2, DEST_LAT + 0.005, DEST_LNG); // ~0.5km

        List<VoteInfo> votes = List.of(
                like(1, 1), like(1, 2), like(1, 3),
                like(2, 1), like(2, 2), like(2, 3)
        );
        Step1Result result = WeightedCostFunction.compute(
                new Step1Input(group(TravelStyle.RELAXED, 1), members(4), List.of(p1, p2), votes));

        assertThat(result.mainPool()).hasSize(2);
        result.mainPool().forEach(p ->
                assertThat(p.priorityScore()).isCloseTo(p.voteScore() * 0.7, within(1e-9)));
    }

    @Test
    void fix14_원거리_장소_거리_페널티_적용() {
        // 가까운 장소(0km)와 먼 장소(~111km)가 섞임 → max_dist >= 3km → 페널티 적용
        PlaceInfo near = place(1, PlaceCategory.CULTURE, 2, DEST_LAT, DEST_LNG);         // 0km
        PlaceInfo far  = place(2, PlaceCategory.CULTURE, 2, DEST_LAT + 1.0, DEST_LNG);  // ~111km

        List<VoteInfo> votes = List.of(
                like(1, 1), like(1, 2), like(1, 3),
                like(2, 1), like(2, 2), like(2, 3)
        );
        Step1Result result = WeightedCostFunction.compute(
                new Step1Input(group(TravelStyle.RELAXED, 1), members(4), List.of(near, far), votes));

        MainPoolPlace nearResult = result.mainPool().stream()
                .filter(p -> p.placeId() == 1L).findFirst().orElseThrow();
        MainPoolPlace farResult  = result.mainPool().stream()
                .filter(p -> p.placeId() == 2L).findFirst().orElseThrow();

        // near: priority = vote_score*0.7 − 0*0.3
        assertThat(nearResult.priorityScore()).isCloseTo(nearResult.voteScore() * 0.7, within(1e-9));
        // far: priority < near (거리 페널티 차감)
        assertThat(farResult.priorityScore()).isLessThan(nearResult.priorityScore());
    }

    // ── [FIX-17] 이상치 마킹 ───────────────────────────────────────────

    @Test
    void fix17_30km_초과_장소_이상치_마킹() {
        PlaceInfo outlier = place(1, PlaceCategory.NATURE, 2, DEST_LAT + 3.0, DEST_LNG); // ~333km
        PlaceInfo normal  = place(2, PlaceCategory.NATURE, 2, DEST_LAT + 0.1, DEST_LNG); // ~11km

        List<VoteInfo> votes = List.of(
                like(1, 1), like(1, 2), like(1, 3),
                like(2, 1), like(2, 2), like(2, 3)
        );
        Step1Result result = WeightedCostFunction.compute(
                new Step1Input(group(TravelStyle.RELAXED, 1), members(4), List.of(outlier, normal), votes));

        MainPoolPlace outlierResult = result.mainPool().stream()
                .filter(p -> p.placeId() == 1L).findFirst().orElseThrow();
        MainPoolPlace normalResult  = result.mainPool().stream()
                .filter(p -> p.placeId() == 2L).findFirst().orElseThrow();

        assertThat(outlierResult.isOutlierCandidate()).isTrue();
        assertThat(normalResult.isOutlierCandidate()).isFalse();
    }

    // ── FOOD 쿼터 ──────────────────────────────────────────────────────

    @Test
    void food_쿼터_초과시_altPool_이동_RELAXED_1일() {
        // RELAXED, 1일 → food_quota = 1 → 2번째 FOOD는 altPool
        PlaceInfo food1 = place(1, PlaceCategory.FOOD, 0, DEST_LAT, DEST_LNG);
        PlaceInfo food2 = place(2, PlaceCategory.FOOD, 0, DEST_LAT + 0.001, DEST_LNG);

        // food1: 4 likes (더 높은 priority), food2: 2 likes
        List<VoteInfo> votes = List.of(
                like(1, 1), like(1, 2), like(1, 3), like(1, 4),
                like(2, 1), like(2, 2)
        );
        Step1Result result = WeightedCostFunction.compute(
                new Step1Input(group(TravelStyle.RELAXED, 1), members(4), List.of(food1, food2), votes));

        assertThat(result.mainPool()).extracting(MainPoolPlace::placeId).containsExactly(1L);
        assertThat(result.altPool()).extracting(AltPoolPlace::placeId).contains(2L);
    }

    @Test
    void food_쿼터_PACKED_1일_2개() {
        // PACKED, 1일 → food_quota = 2 → 3개 중 2개 mainPool, 1개 altPool
        PlaceInfo food1 = place(1, PlaceCategory.FOOD, 0, DEST_LAT, DEST_LNG);
        PlaceInfo food2 = place(2, PlaceCategory.FOOD, 0, DEST_LAT + 0.001, DEST_LNG);
        PlaceInfo food3 = place(3, PlaceCategory.FOOD, 0, DEST_LAT + 0.002, DEST_LNG);

        List<VoteInfo> votes = List.of(
                like(1, 1), like(1, 2), like(1, 3), like(1, 4),
                like(2, 1), like(2, 2), like(2, 3),
                like(3, 1), like(3, 2)  // 2 likes (threshold=2)
        );
        Step1Result result = WeightedCostFunction.compute(
                new Step1Input(group(TravelStyle.PACKED, 1), members(4), List.of(food1, food2, food3), votes));

        assertThat(result.mainPool()).extracting(MainPoolPlace::placeId).containsExactlyInAnyOrder(1L, 2L);
        assertThat(result.altPool()).extracting(AltPoolPlace::placeId).contains(3L);
    }

    @Test
    void food_쿼터_다일_계획_누적() {
        // RELAXED, 3일 → food_quota = 3
        List<PlaceInfo> places = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            places.add(place(i, PlaceCategory.FOOD, 0, DEST_LAT + i * 0.001, DEST_LNG));
        }
        List<VoteInfo> votes = new ArrayList<>();
        for (PlaceInfo p : places) {
            for (int u = 1; u <= 3; u++) votes.add(like(p.placeId(), u)); // 3 likes each
        }
        Step1Result result = WeightedCostFunction.compute(
                new Step1Input(group(TravelStyle.RELAXED, 3), members(4), places, votes));

        // food_quota = 1*3 = 3 → 4번째 FOOD는 altPool
        assertThat(result.mainPool()).filteredOn(p -> true).hasSize(3);
        assertThat(result.altPool()).extracting(AltPoolPlace::placeId).contains(4L);
    }

    // ── Density 한도 ───────────────────────────────────────────────────

    @Test
    void density_한도_초과_비FOOD_장소_altPool() {
        // RELAXED, 1일 → budget = 5*1 = 5
        // ACTIVITY(density=3) + ACTIVITY(density=3) = 6 > 5 → 2번째 altPool
        PlaceInfo act1 = place(1, PlaceCategory.ACTIVITY, 3, DEST_LAT, DEST_LNG);
        PlaceInfo act2 = place(2, PlaceCategory.ACTIVITY, 3, DEST_LAT + 0.001, DEST_LNG);

        List<VoteInfo> votes = List.of(
                like(1, 1), like(1, 2), like(1, 3), like(1, 4), // 4 likes → 높은 priority
                like(2, 1), like(2, 2), like(2, 3)
        );
        Step1Result result = WeightedCostFunction.compute(
                new Step1Input(group(TravelStyle.RELAXED, 1), members(4), List.of(act1, act2), votes));

        assertThat(result.mainPool()).extracting(MainPoolPlace::placeId).contains(1L);
        assertThat(result.altPool()).extracting(AltPoolPlace::placeId).contains(2L);
        assertThat(result.meta().densityLimit()).isEqualTo(5);
    }

    @Test
    void density_한도_PACKED_더_넉넉() {
        // PACKED, 1일 → budget = 8*1 = 8
        // ACTIVITY(3) + CULTURE(2) + CULTURE(2) = 7 ≤ 8 → 모두 mainPool
        PlaceInfo act    = place(1, PlaceCategory.ACTIVITY, 3, DEST_LAT, DEST_LNG);
        PlaceInfo cult1  = place(2, PlaceCategory.CULTURE,  2, DEST_LAT + 0.001, DEST_LNG);
        PlaceInfo cult2  = place(3, PlaceCategory.CULTURE,  2, DEST_LAT + 0.002, DEST_LNG);

        List<VoteInfo> votes = List.of(
                like(1, 1), like(1, 2), like(1, 3),
                like(2, 1), like(2, 2), like(2, 3),
                like(3, 1), like(3, 2), like(3, 3)
        );
        Step1Result result = WeightedCostFunction.compute(
                new Step1Input(group(TravelStyle.PACKED, 1), members(4), List.of(act, cult1, cult2), votes));

        assertThat(result.mainPool()).extracting(MainPoolPlace::placeId).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(result.meta().densityLimit()).isEqualTo(8);
    }

    // ── K 계산 ─────────────────────────────────────────────────────────

    @Test
    void K_여행일수_당일치기_1일() {
        GroupInfo g = new GroupInfo(1L, DEST_LAT, DEST_LNG, TravelStyle.RELAXED,
                LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 1), false);
        Step1Result result = WeightedCostFunction.compute(
                new Step1Input(g, members(4), List.of(), List.of()));
        assertThat(result.meta().K()).isEqualTo(1);
    }

    @Test
    void K_여행일수_3박4일() {
        GroupInfo g = new GroupInfo(1L, DEST_LAT, DEST_LNG, TravelStyle.RELAXED,
                LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 4), false);
        Step1Result result = WeightedCostFunction.compute(
                new Step1Input(g, members(4), List.of(), List.of()));
        assertThat(result.meta().K()).isEqualTo(4);
    }

    // ── [FIX-13] Tie-breaker 정렬 ─────────────────────────────────────

    @Test
    void fix13_같은_위치_동일_좋아요율_like_count_많은_쪽_우선() {
        // 같은 위치 → dist 동일 → priority_score 차이는 like_count에서 발생
        PlaceInfo p1 = place(1, PlaceCategory.CULTURE, 2, DEST_LAT, DEST_LNG);
        PlaceInfo p2 = place(2, PlaceCategory.CULTURE, 2, DEST_LAT, DEST_LNG);

        // p1: 4 likes (4/4 = 100% → vote_score 2.0)
        // p2: 3 likes (3/3 = 100% → vote_score 2.0)
        // priority_score 동일, like_count p1 > p2 → p1 먼저
        List<VoteInfo> votes = List.of(
                like(1, 1), like(1, 2), like(1, 3), like(1, 4),
                like(2, 1), like(2, 2), like(2, 3)
        );
        // density: RELAXED 1일, 2+2=4 ≤ 5 → 둘 다 mainPool
        Step1Result result = WeightedCostFunction.compute(
                new Step1Input(group(TravelStyle.RELAXED, 1), members(4), List.of(p1, p2), votes));

        List<Long> ids = result.mainPool().stream()
                .map(MainPoolPlace::placeId).collect(Collectors.toList());
        assertThat(ids.indexOf(1L)).isLessThan(ids.indexOf(2L));
    }

    @Test
    void fix13_모든_조건_동일하면_place_id_ASC() {
        PlaceInfo p1 = place(1, PlaceCategory.CULTURE, 2, DEST_LAT, DEST_LNG);
        PlaceInfo p2 = place(2, PlaceCategory.CULTURE, 2, DEST_LAT, DEST_LNG);

        // 동일 like_count, 동일 위치 → place_id ASC
        List<VoteInfo> votes = List.of(
                like(1, 1), like(1, 2), like(1, 3),
                like(2, 1), like(2, 2), like(2, 3)
        );
        Step1Result result = WeightedCostFunction.compute(
                new Step1Input(group(TravelStyle.RELAXED, 1), members(4), List.of(p2, p1), votes));

        List<Long> ids = result.mainPool().stream()
                .map(MainPoolPlace::placeId).collect(Collectors.toList());
        assertThat(ids).containsExactly(1L, 2L);
    }

    // ── altPool 순위 ───────────────────────────────────────────────────

    @Test
    void altPool_순위_1부터_연속_부여() {
        // 4멤버: alt_min=1, alt_max=1
        // p1, p2: 각 1 like → alt_rescue, vote_score=2.0>0
        PlaceInfo p1 = place(1, PlaceCategory.CULTURE, 2, DEST_LAT, DEST_LNG);
        PlaceInfo p2 = place(2, PlaceCategory.CULTURE, 2, DEST_LAT + 0.001, DEST_LNG);

        List<VoteInfo> votes = List.of(like(1, 1), like(2, 2));
        Step1Result result = WeightedCostFunction.compute(
                new Step1Input(group(TravelStyle.RELAXED, 1), members(4), List.of(p1, p2), votes));

        assertThat(result.altPool()).hasSize(2);
        List<Integer> ranks = result.altPool().stream()
                .map(AltPoolPlace::altRank).sorted().collect(Collectors.toList());
        assertThat(ranks).containsExactly(1, 2);
    }

    // ── 메타 정보 ──────────────────────────────────────────────────────

    @Test
    void meta_PACKED_6멤버_정보_정확성() {
        Step1Result result = WeightedCostFunction.compute(
                new Step1Input(group(TravelStyle.PACKED, 3), members(6), List.of(), List.of()));

        Step1Meta meta = result.meta();
        assertThat(meta.K()).isEqualTo(3);
        assertThat(meta.totalMembers()).isEqualTo(6);
        assertThat(meta.passedThreshold()).isEqualTo(3); // ceil(6*0.5)
        assertThat(meta.densityLimit()).isEqualTo(8);    // PACKED
        assertThat(meta.foodPerDayQuota()).isEqualTo(2); // PACKED
        assertThat(meta.destinationLat()).isEqualTo(DEST_LAT);
        assertThat(meta.destinationLng()).isEqualTo(DEST_LNG);
    }

    // ── Haversine 거리 계산 ───────────────────────────────────────────

    @Test
    void haversine_서울에서_부산_약_325km() {
        double dist = WeightedCostFunction.haversine(37.5665, 126.9780, 35.1796, 129.0756);
        assertThat(dist).isBetween(320.0, 330.0);
    }

    @Test
    void haversine_같은_좌표_0km() {
        double dist = WeightedCostFunction.haversine(DEST_LAT, DEST_LNG, DEST_LAT, DEST_LNG);
        assertThat(dist).isCloseTo(0.0, within(1e-9));
    }

    // ── 통합 시나리오 ───────────────────────────────────────────────────

    @Test
    void 통합_6멤버_2일_RELAXED_복합_시나리오() {
        // 6멤버, 2일, RELAXED
        // passed_threshold=3, alt=[1,2], density_budget=5*2=10, food_quota=1*2=2
        PlaceInfo food1  = place(1, PlaceCategory.FOOD,     0, DEST_LAT, DEST_LNG);
        PlaceInfo food2  = place(2, PlaceCategory.FOOD,     0, DEST_LAT, DEST_LNG);
        PlaceInfo food3  = place(3, PlaceCategory.FOOD,     0, DEST_LAT, DEST_LNG); // food 쿼터 초과
        PlaceInfo act1   = place(4, PlaceCategory.ACTIVITY, 3, DEST_LAT, DEST_LNG);
        PlaceInfo cult1  = place(5, PlaceCategory.CULTURE,  2, DEST_LAT, DEST_LNG);
        PlaceInfo cult2  = place(6, PlaceCategory.CULTURE,  2, DEST_LAT, DEST_LNG);
        PlaceInfo shop1  = place(7, PlaceCategory.SHOPPING, 1, DEST_LAT, DEST_LNG);
        PlaceInfo rescue = place(8, PlaceCategory.NATURE,   2, DEST_LAT, DEST_LNG); // alt_rescue

        List<VoteInfo> votes = new ArrayList<>();
        // food1~3, act1, cult1~2, shop1: 4 likes (passed)
        for (int id = 1; id <= 7; id++) {
            for (int u = 1; u <= 4; u++) votes.add(like(id, u));
        }
        // rescue: 2 likes (alt 범위 [1,2], vote_score = 2.0 > 0)
        votes.add(like(8, 1));
        votes.add(like(8, 2));

        Step1Result result = WeightedCostFunction.compute(
                new Step1Input(group(TravelStyle.RELAXED, 2), members(6),
                        List.of(food1, food2, food3, act1, cult1, cult2, shop1, rescue), votes));

        // food_quota=2 → food1, food2 mainPool / food3 altPool
        assertThat(result.mainPool()).extracting(MainPoolPlace::placeId).contains(1L, 2L);
        assertThat(result.altPool()).extracting(AltPoolPlace::placeId).contains(3L);

        // density: act1(3)+cult1(2)+cult2(2)+shop1(1) = 8 ≤ 10 → 모두 mainPool
        assertThat(result.mainPool()).extracting(MainPoolPlace::placeId)
                .containsAll(List.of(4L, 5L, 6L, 7L));

        // rescue: alt_rescue → altPool
        assertThat(result.altPool()).extracting(AltPoolPlace::placeId).contains(8L);

        // alt_rank는 1부터 연속
        List<Integer> ranks = result.altPool().stream()
                .map(AltPoolPlace::altRank).sorted().collect(Collectors.toList());
        for (int i = 0; i < ranks.size(); i++) {
            assertThat(ranks.get(i)).isEqualTo(i + 1);
        }
    }
}