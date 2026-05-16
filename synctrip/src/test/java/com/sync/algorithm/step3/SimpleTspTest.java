package com.sync.algorithm.step3;

import com.sync.algorithm.PlaceCategory;
import com.sync.algorithm.step1.MainPoolPlace;
import com.sync.algorithm.step2.AssignedPlace;
import com.sync.algorithm.step2.DayGroup;
import com.sync.algorithm.step2.Step2Result;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleTspTest {

    // ── 헬퍼 ────────────────────────────────────────────────────────────

    /** day=1, isOutlier=false 고정 */
    private AssignedPlace ap(long id, double lat, double lng, int duration) {
        return new AssignedPlace(id, 1, PlaceCategory.CULTURE, lat, lng, duration, 1.0 / id, false);
    }

    private AssignedPlace ap(long id, PlaceCategory cat, double lat, double lng, int duration) {
        return new AssignedPlace(id, 1, cat, lat, lng, duration, 1.0 / id, false);
    }

    private Step3Result run(List<DayGroup> days, boolean isOverseas,
                             LocalTime start, Map<Long, OpeningHours> hours) {
        Step2Result step2 = new Step2Result(days, List.of());
        return SimpleTsp.schedule(new Step3Input(step2, isOverseas, start, hours));
    }

    private Step3Result run(List<DayGroup> days) {
        return run(days, false, SimpleTsp.DEFAULT_DAY_START, Map.of());
    }

    // ── 빈 일차 ──────────────────────────────────────────────────────────

    @Test
    void 빈_DayGroup이면_빈_DaySchedule_반환() {
        List<DayGroup> days = List.of(new DayGroup(1, List.of()));

        Step3Result result = run(days);

        assertThat(result.daySchedules()).hasSize(1);
        assertThat(result.daySchedules().get(0).places()).isEmpty();
    }

    @Test
    void 빈_일차가_여럿_있어도_크래시_없음() {
        List<DayGroup> days = List.of(
                new DayGroup(1, List.of()),
                new DayGroup(2, List.of()),
                new DayGroup(3, List.of())
        );

        Step3Result result = run(days);

        assertThat(result.daySchedules()).hasSize(3);
        assertThat(result.daySchedules()).allMatch(ds -> ds.places().isEmpty());
    }

    // ── 단일 장소 ────────────────────────────────────────────────────────

    @Test
    void 단일_장소_orderInDay_1_시간_할당_정확() {
        AssignedPlace p = ap(1, 37.5, 127.0, 90);
        List<DayGroup> days = List.of(new DayGroup(1, List.of(p)));

        Step3Result result = run(days);

        ScheduledPlace sp = result.daySchedules().get(0).places().get(0);
        assertThat(sp.orderInDay()).isEqualTo(1);
        assertThat(sp.startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(sp.endTime()).isEqualTo(LocalTime.of(10, 30));
    }

    // ── Nearest Neighbor TSP 경로 ────────────────────────────────────────

    @Test
    void TSP_가장_가까운_장소_순서로_정렬() {
        // P1(0,0) → 시작점 (가장 높은 우선순위)
        // P2(0,5) → P1에서 멀리
        // P3(0,0.5) → P1에서 가까움
        //
        // Nearest Neighbor: P1 → P3(0.5) → P2(4.5)
        // 원래 순서(P1,P2,P3)와 다름 → TSP가 재정렬했는지 검증
        AssignedPlace p1 = ap(1, 0.0, 0.0,  60);
        AssignedPlace p2 = ap(2, 0.0, 5.0,  60);
        AssignedPlace p3 = ap(3, 0.0, 0.5,  60);
        List<DayGroup> days = List.of(new DayGroup(1, List.of(p1, p2, p3)));

        Step3Result result = run(days);

        List<ScheduledPlace> places = result.daySchedules().get(0).places();
        assertThat(places.get(0).placeId()).isEqualTo(1L); // 시작점
        assertThat(places.get(1).placeId()).isEqualTo(3L); // P1에서 가장 가까운 P3
        assertThat(places.get(2).placeId()).isEqualTo(2L); // 마지막
    }

    @Test
    void TSP_선형_배치_최단_경로_확인() {
        // P1(0,0) → P2(0,0.1) → P3(0,0.3) — 한 줄로 배치
        // Nearest Neighbor: 순서 그대로
        AssignedPlace p1 = ap(1, 0.0, 0.0,  60);
        AssignedPlace p2 = ap(2, 0.0, 0.1,  60);
        AssignedPlace p3 = ap(3, 0.0, 0.3,  60);
        List<DayGroup> days = List.of(new DayGroup(1, List.of(p1, p2, p3)));

        Step3Result result = run(days);

        List<ScheduledPlace> places = result.daySchedules().get(0).places();
        assertThat(places.get(0).placeId()).isEqualTo(1L);
        assertThat(places.get(1).placeId()).isEqualTo(2L);
        assertThat(places.get(2).placeId()).isEqualTo(3L);
    }

    // ── 시간 할당 연쇄 ───────────────────────────────────────────────────

    @Test
    void 시간_할당이_이전_종료_시간을_시작_시간으로_연결() {
        AssignedPlace p1 = ap(1, 0.0, 0.0, 60);   // 09:00 ~ 10:00
        AssignedPlace p2 = ap(2, 0.0, 0.0, 90);   // 10:00 ~ 11:30
        AssignedPlace p3 = ap(3, 0.0, 0.0, 30);   // 11:30 ~ 12:00
        List<DayGroup> days = List.of(new DayGroup(1, List.of(p1, p2, p3)));

        Step3Result result = run(days);

        List<ScheduledPlace> places = result.daySchedules().get(0).places();
        assertThat(places.get(0).startTime()).isEqualTo(LocalTime.of(9,  0));
        assertThat(places.get(0).endTime())  .isEqualTo(LocalTime.of(10, 0));
        assertThat(places.get(1).startTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(places.get(1).endTime())  .isEqualTo(LocalTime.of(11, 30));
        assertThat(places.get(2).startTime()).isEqualTo(LocalTime.of(11, 30));
        assertThat(places.get(2).endTime())  .isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    void 커스텀_dayStartTime_적용() {
        AssignedPlace p = ap(1, 0.0, 0.0, 60);
        List<DayGroup> days = List.of(new DayGroup(1, List.of(p)));

        Step3Result result = run(days, false, LocalTime.of(8, 0), Map.of());

        ScheduledPlace sp = result.daySchedules().get(0).places().get(0);
        assertThat(sp.startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(sp.endTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void dayStartTime_null이면_기본값_09시_적용() {
        AssignedPlace p = ap(1, 0.0, 0.0, 60);
        Step2Result step2 = new Step2Result(List.of(new DayGroup(1, List.of(p))), List.of());
        Step3Result result = SimpleTsp.schedule(new Step3Input(step2, false, null, Map.of()));

        assertThat(result.daySchedules().get(0).places().get(0).startTime())
                .isEqualTo(LocalTime.of(9, 0));
    }

    // ── orderInDay 번호 ──────────────────────────────────────────────────

    @Test
    void orderInDay가_1부터_순서대로_부여됨() {
        List<DayGroup> days = List.of(new DayGroup(1, List.of(
                ap(1, 0.0, 0.0, 30),
                ap(2, 0.0, 0.1, 30),
                ap(3, 0.0, 0.2, 30)
        )));

        List<ScheduledPlace> places = run(days).daySchedules().get(0).places();

        assertThat(places.get(0).orderInDay()).isEqualTo(1);
        assertThat(places.get(1).orderInDay()).isEqualTo(2);
        assertThat(places.get(2).orderInDay()).isEqualTo(3);
    }

    // ── 해외 영업시간 체크 ───────────────────────────────────────────────

    @Test
    void 해외_영업시간_내에_있으면_violation_false() {
        // 09:00~10:00 방문, 영업시간 08:00~18:00 → 정상
        AssignedPlace p = ap(1, 0.0, 0.0, 60);
        OpeningHours oh = new OpeningHours(LocalTime.of(8, 0), LocalTime.of(18, 0));

        Step3Result result = run(
                List.of(new DayGroup(1, List.of(p))),
                true, SimpleTsp.DEFAULT_DAY_START, Map.of(1L, oh));

        assertThat(result.daySchedules().get(0).places().get(0).openingHoursViolation())
                .isFalse();
    }

    @Test
    void 해외_영업시간_이전에_시작하면_violation_true() {
        // 09:00 방문, 영업시간 10:00~18:00 → 오픈 전
        AssignedPlace p = ap(1, 0.0, 0.0, 60);
        OpeningHours oh = new OpeningHours(LocalTime.of(10, 0), LocalTime.of(18, 0));

        Step3Result result = run(
                List.of(new DayGroup(1, List.of(p))),
                true, SimpleTsp.DEFAULT_DAY_START, Map.of(1L, oh));

        assertThat(result.daySchedules().get(0).places().get(0).openingHoursViolation())
                .isTrue();
    }

    @Test
    void 해외_영업시간_이후에_종료하면_violation_true() {
        // 09:00~10:00 방문, 영업시간 09:00~09:30 → 클로즈 후 종료
        AssignedPlace p = ap(1, 0.0, 0.0, 60);
        OpeningHours oh = new OpeningHours(LocalTime.of(9, 0), LocalTime.of(9, 30));

        Step3Result result = run(
                List.of(new DayGroup(1, List.of(p))),
                true, SimpleTsp.DEFAULT_DAY_START, Map.of(1L, oh));

        assertThat(result.daySchedules().get(0).places().get(0).openingHoursViolation())
                .isTrue();
    }

    @Test
    void 해외라도_영업시간_데이터_없으면_violation_false() {
        AssignedPlace p = ap(1, 0.0, 0.0, 60);

        // 장소 1의 영업시간 데이터 없음
        Step3Result result = run(
                List.of(new DayGroup(1, List.of(p))),
                true, SimpleTsp.DEFAULT_DAY_START, Map.of());

        assertThat(result.daySchedules().get(0).places().get(0).openingHoursViolation())
                .isFalse();
    }

    @Test
    void 국내_isOverseas_false면_violation_항상_false() {
        // 영업시간 데이터가 있어도 국내면 체크 안 함
        AssignedPlace p = ap(1, 0.0, 0.0, 60);
        OpeningHours oh = new OpeningHours(LocalTime.of(10, 0), LocalTime.of(18, 0));

        Step3Result result = run(
                List.of(new DayGroup(1, List.of(p))),
                false, SimpleTsp.DEFAULT_DAY_START, Map.of(1L, oh));

        assertThat(result.daySchedules().get(0).places().get(0).openingHoursViolation())
                .isFalse();
    }

    // ── ScheduledPlace 필드 전달 ─────────────────────────────────────────

    @Test
    void AssignedPlace_메타_정보가_ScheduledPlace에_올바르게_복사됨() {
        AssignedPlace p = new AssignedPlace(
                42L, 1, PlaceCategory.ACTIVITY,
                35.1, 129.0, 120, 0.85, true);
        List<DayGroup> days = List.of(new DayGroup(1, List.of(p)));

        ScheduledPlace sp = run(days).daySchedules().get(0).places().get(0);

        assertThat(sp.placeId()).isEqualTo(42L);
        assertThat(sp.day()).isEqualTo(1);
        assertThat(sp.category()).isEqualTo(PlaceCategory.ACTIVITY);
        assertThat(sp.latitude()).isEqualTo(35.1);
        assertThat(sp.longitude()).isEqualTo(129.0);
        assertThat(sp.estimatedDuration()).isEqualTo(120);
        assertThat(sp.priorityScore()).isEqualTo(0.85);
        assertThat(sp.isOutlierCandidate()).isTrue();
    }

    // ── 다중 일차 ────────────────────────────────────────────────────────

    @Test
    void 다중_일차는_각각_독립적으로_스케줄링() {
        // day1: P1, P2 / day2: P3
        // 각 일차가 동일한 dayStart(09:00)에서 독립적으로 시작해야 함
        AssignedPlace p1 = ap(1, 0.0, 0.0, 60);
        AssignedPlace p2 = ap(2, 0.0, 0.1, 60);
        AssignedPlace p3 = ap(3, 1.0, 0.0, 90);

        List<DayGroup> days = List.of(
                new DayGroup(1, List.of(p1, p2)),
                new DayGroup(2, List.of(p3))
        );

        Step3Result result = run(days);

        assertThat(result.daySchedules()).hasSize(2);
        // day1 첫 번째 장소: 09:00 시작
        assertThat(result.daySchedules().get(0).places().get(0).startTime())
                .isEqualTo(LocalTime.of(9, 0));
        // day2도 09:00에서 독립적으로 시작
        assertThat(result.daySchedules().get(1).places().get(0).startTime())
                .isEqualTo(LocalTime.of(9, 0));
        assertThat(result.daySchedules().get(1).places().get(0).endTime())
                .isEqualTo(LocalTime.of(10, 30));
    }

    @Test
    void 일차_번호가_DaySchedule에_정확히_유지됨() {
        List<DayGroup> days = List.of(
                new DayGroup(1, List.of(ap(1, 0.0, 0.0, 30))),
                new DayGroup(2, List.of(ap(2, 0.0, 0.0, 30))),
                new DayGroup(3, List.of())
        );

        Step3Result result = run(days);

        assertThat(result.daySchedules().get(0).day()).isEqualTo(1);
        assertThat(result.daySchedules().get(1).day()).isEqualTo(2);
        assertThat(result.daySchedules().get(2).day()).isEqualTo(3);
    }

    // ── overflow 전달 ────────────────────────────────────────────────────

    @Test
    void Step2_overflow가_Step3Result로_그대로_전달됨() {
        MainPoolPlace overflowPlace = new MainPoolPlace(99L, 0.3, 0.21, 1, 0, 5.0, false);
        Step2Result step2 = new Step2Result(
                List.of(new DayGroup(1, List.of(ap(1, 0.0, 0.0, 60)))),
                List.of(overflowPlace)
        );

        Step3Result result = SimpleTsp.schedule(
                new Step3Input(step2, false, SimpleTsp.DEFAULT_DAY_START, Map.of()));

        assertThat(result.overflow()).hasSize(1);
        assertThat(result.overflow().get(0).placeId()).isEqualTo(99L);
    }

    // ── 결정론성 ─────────────────────────────────────────────────────────

    @Test
    void 결정론성_같은_입력이면_항상_같은_출력() {
        List<DayGroup> days = List.of(new DayGroup(1, List.of(
                ap(1, 0.0, 0.0,  60),
                ap(2, 0.0, 5.0,  90),
                ap(3, 0.0, 0.5, 120)
        )));

        Step3Result r1 = run(days);
        Step3Result r2 = run(days);

        assertThat(r1.daySchedules()).isEqualTo(r2.daySchedules());
    }
}
