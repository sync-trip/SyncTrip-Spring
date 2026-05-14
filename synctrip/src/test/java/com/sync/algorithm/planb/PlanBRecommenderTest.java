package com.sync.algorithm.planb;

import com.sync.algorithm.PlaceCategory;
import com.sync.algorithm.step1.AltPoolPlace;
import com.sync.algorithm.step1.MainPoolPlace;
import com.sync.algorithm.step1.PlaceInfo;
import com.sync.algorithm.step3.*;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanBRecommenderTest {

    // ── 헬퍼 ────────────────────────────────────────────────────────────

    private PlaceInfo place(long id, PlaceCategory cat, double lat, double lng) {
        return new PlaceInfo(id, 1L, "P" + id, cat, 1, 60, lat, lng);
    }

    private AltPoolPlace alt(long id, double priority) {
        return new AltPoolPlace(id, priority, priority * 0.7, 2, 1);
    }

    private MainPoolPlace overflow(long id, double priority) {
        return new MainPoolPlace(id, priority, priority * 0.7, 3, 0, 1.0, false);
    }

    /** 스케줄에 placeId 하나만 배정된 최소 Step3Result */
    private Step3Result step3WithScheduled(long scheduledId, List<MainPoolPlace> overflowList) {
        ScheduledPlace sp = new ScheduledPlace(
                scheduledId, 1, 1, PlaceCategory.CULTURE,
                37.5, 127.0, 60, LocalTime.of(9, 0), LocalTime.of(10, 0),
                1.0, false, false);
        DaySchedule ds = new DaySchedule(1, List.of(sp));
        return new Step3Result(List.of(ds), overflowList);
    }

    private Step3Result emptyStep3(List<MainPoolPlace> overflowList) {
        return new Step3Result(List.of(new DaySchedule(1, List.of())), overflowList);
    }

    // ── targetPlaceId 오류 ───────────────────────────────────────────────

    @Test
    void targetPlaceId가_places에_없으면_IllegalArgumentException() {
        Step3Result step3 = emptyStep3(List.of());
        PlanBInput input = new PlanBInput(step3, List.of(), List.of(), 999L);

        assertThatThrownBy(() -> PlanBRecommender.recommend(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999");
    }

    // ── 후보 없음 ────────────────────────────────────────────────────────

    @Test
    void altPool과_overflow_모두_비어있으면_빈_결과() {
        List<PlaceInfo> places = List.of(place(1, PlaceCategory.CULTURE, 37.5, 127.0));
        Step3Result step3 = emptyStep3(List.of());
        PlanBInput input = new PlanBInput(step3, List.of(), places, 1L);

        assertThat(PlanBRecommender.recommend(input).recommendations()).isEmpty();
    }

    @Test
    void 모든_후보가_이미_스케줄에_있으면_빈_결과() {
        // P1=target(스케줄), P2=altPool이지만 이미 스케줄에 포함 (같은 카테고리, 1km 이내)
        PlaceInfo target = place(1, PlaceCategory.CULTURE, 37.5, 127.0);
        PlaceInfo p2    = place(2, PlaceCategory.CULTURE, 37.5, 127.005); // ~0.44km

        ScheduledPlace sp1 = new ScheduledPlace(1L, 1, 1, PlaceCategory.CULTURE,
                37.5, 127.0, 60, LocalTime.of(9, 0), LocalTime.of(10, 0), 1.0, false, false);
        ScheduledPlace sp2 = new ScheduledPlace(2L, 1, 2, PlaceCategory.CULTURE,
                37.5, 127.005, 60, LocalTime.of(10, 22), LocalTime.of(11, 22), 0.8, false, false);
        Step3Result step3 = new Step3Result(
                List.of(new DaySchedule(1, List.of(sp1, sp2))), List.of());

        PlanBInput input = new PlanBInput(
                step3, List.of(alt(2, 0.8)), List.of(target, p2), 1L);

        assertThat(PlanBRecommender.recommend(input).recommendations()).isEmpty();
    }

    // ── 추천 점수 및 순서 ────────────────────────────────────────────────

    @Test
    void altPool_후보가_순위_맞게_정렬됨() {
        // P1=target(스케줄), P2/P3=altPool 후보 — 같은 카테고리, 1km 이내
        // P2: priority 높음, 가까움 → 높은 점수
        // P3: priority 낮음, 조금 더 멀지만 1km 이내 → 낮은 점수
        PlaceInfo target = place(1, PlaceCategory.CULTURE, 37.5, 127.0);
        PlaceInfo p2    = place(2, PlaceCategory.CULTURE, 37.5, 127.005);  // ~0.44km
        PlaceInfo p3    = place(3, PlaceCategory.CULTURE, 37.505, 127.0);  // ~0.56km

        Step3Result step3 = step3WithScheduled(1L, List.of());
        PlanBInput input = new PlanBInput(
                step3,
                List.of(alt(2, 1.0), alt(3, 0.5)),
                List.of(target, p2, p3),
                1L);

        List<PlanBCandidate> recs = PlanBRecommender.recommend(input).recommendations();

        assertThat(recs).hasSize(2);
        assertThat(recs.get(0).placeId()).isEqualTo(2L);
        assertThat(recs.get(1).placeId()).isEqualTo(3L);
    }

    @Test
    void overflow_후보_fromOverflow_플래그가_true() {
        PlaceInfo target = place(1, PlaceCategory.CULTURE, 37.5, 127.0);
        PlaceInfo p2    = place(2, PlaceCategory.CULTURE, 37.5, 127.005); // ~0.44km

        Step3Result step3 = step3WithScheduled(1L, List.of(overflow(2, 0.7)));
        PlanBInput input = new PlanBInput(step3, List.of(), List.of(target, p2), 1L);

        PlanBCandidate rec = PlanBRecommender.recommend(input).recommendations().get(0);

        assertThat(rec.placeId()).isEqualTo(2L);
        assertThat(rec.fromOverflow()).isTrue();
    }

    @Test
    void altPool_후보_fromOverflow_플래그가_false() {
        PlaceInfo target = place(1, PlaceCategory.CULTURE, 37.5, 127.0);
        PlaceInfo p2    = place(2, PlaceCategory.CULTURE, 37.5, 127.005); // ~0.44km

        Step3Result step3 = step3WithScheduled(1L, List.of());
        PlanBInput input = new PlanBInput(
                step3, List.of(alt(2, 0.7)), List.of(target, p2), 1L);

        assertThat(PlanBRecommender.recommend(input).recommendations().get(0).fromOverflow())
                .isFalse();
    }

    @Test
    void altPool과_overflow_혼합_후보_통합_정렬() {
        // P2=altPool(낮은 priority), P3=overflow(높은 priority) — 같은 카테고리, 1km 이내
        PlaceInfo target = place(1, PlaceCategory.CULTURE, 37.5, 127.0);
        PlaceInfo p2    = place(2, PlaceCategory.CULTURE, 37.5, 127.004);  // ~0.35km
        PlaceInfo p3    = place(3, PlaceCategory.CULTURE, 37.5, 127.005);  // ~0.44km

        Step3Result step3 = step3WithScheduled(1L, List.of(overflow(3, 1.2)));
        PlanBInput input = new PlanBInput(
                step3, List.of(alt(2, 0.3)), List.of(target, p2, p3), 1L);

        List<PlanBCandidate> recs = PlanBRecommender.recommend(input).recommendations();

        assertThat(recs).hasSize(2);
        assertThat(recs.get(0).placeId()).isEqualTo(3L); // overflow지만 score 더 높음
        assertThat(recs.get(1).placeId()).isEqualTo(2L);
    }

    // ── MAX_RECOMMENDATIONS 상한 ─────────────────────────────────────────

    @Test
    void 후보가_8개이면_7개만_반환() {
        // id*0.001° × ~88.5km/° ≈ id*0.09km — 모두 1km 이내, 같은 카테고리
        PlaceInfo target = place(1, PlaceCategory.CULTURE, 37.5, 127.0);
        List<PlaceInfo> places = new java.util.ArrayList<>(List.of(target));
        List<AltPoolPlace> altPool = new java.util.ArrayList<>();
        for (long id = 2; id <= 9; id++) {
            places.add(place(id, PlaceCategory.CULTURE, 37.5, 127.0 + id * 0.001));
            altPool.add(alt(id, 1.0 / id));
        }

        Step3Result step3 = step3WithScheduled(1L, List.of());
        PlanBInput input = new PlanBInput(step3, altPool, places, 1L);

        assertThat(PlanBRecommender.recommend(input).recommendations()).hasSize(7);
    }

    // ── 거리 계산 및 필드 ────────────────────────────────────────────────

    @Test
    void 동일_좌표_후보는_distanceKmToTarget이_0에_가까움() {
        PlaceInfo target = place(1, PlaceCategory.CULTURE, 37.5, 127.0);
        PlaceInfo p2    = place(2, PlaceCategory.CULTURE, 37.5, 127.0); // 동일 위치

        Step3Result step3 = step3WithScheduled(1L, List.of());
        PlanBInput input = new PlanBInput(
                step3, List.of(alt(2, 0.5)), List.of(target, p2), 1L);

        PlanBCandidate rec = PlanBRecommender.recommend(input).recommendations().get(0);

        assertThat(rec.distanceKmToTarget()).isLessThan(0.001);
    }

    @Test
    void PlanBCandidate_메타_필드가_올바르게_채워짐() {
        PlaceInfo target = place(1, PlaceCategory.ACTIVITY, 37.5, 127.0);
        PlaceInfo p2    = new PlaceInfo(2L, 1L, "P2", PlaceCategory.ACTIVITY, 2, 90, 37.5, 127.005);

        Step3Result step3 = step3WithScheduled(1L, List.of());
        PlanBInput input = new PlanBInput(
                step3, List.of(alt(2, 0.8)), List.of(target, p2), 1L);

        PlanBCandidate rec = PlanBRecommender.recommend(input).recommendations().get(0);

        assertThat(rec.placeId()).isEqualTo(2L);
        assertThat(rec.category()).isEqualTo(PlaceCategory.ACTIVITY);
        assertThat(rec.estimatedDuration()).isEqualTo(90);
        assertThat(rec.recommendScore()).isGreaterThan(0.0);
    }

    // ── places에 없는 후보는 조용히 제외 ────────────────────────────────

    @Test
    void places에_없는_altPool_항목은_조용히_제외() {
        PlaceInfo target = place(1, PlaceCategory.CULTURE, 37.5, 127.0);
        // P2는 altPool에 있지만 places에는 없음
        Step3Result step3 = step3WithScheduled(1L, List.of());
        PlanBInput input = new PlanBInput(
                step3, List.of(alt(2, 0.8)), List.of(target), 1L);

        assertThat(PlanBRecommender.recommend(input).recommendations()).isEmpty();
    }

    // ── 결정론성 ─────────────────────────────────────────────────────────

    @Test
    void 결정론성_같은_입력이면_항상_같은_출력() {
        PlaceInfo target = place(1, PlaceCategory.CULTURE, 37.5, 127.0);
        PlaceInfo p2    = place(2, PlaceCategory.CULTURE, 37.5, 127.005);   // ~0.44km, altPool
        PlaceInfo p3    = place(3, PlaceCategory.CULTURE, 37.504, 127.0);   // ~0.44km, overflow

        Step3Result step3 = step3WithScheduled(1L, List.of(overflow(3, 0.6)));
        PlanBInput input = new PlanBInput(
                step3, List.of(alt(2, 0.8)), List.of(target, p2, p3), 1L);

        assertThat(PlanBRecommender.recommend(input).recommendations())
                .isEqualTo(PlanBRecommender.recommend(input).recommendations());
    }
}
