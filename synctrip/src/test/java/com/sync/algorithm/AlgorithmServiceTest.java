package com.sync.algorithm;

import com.sync.algorithm.step1.GroupInfo;
import com.sync.algorithm.step1.MemberInfo;
import com.sync.algorithm.step1.PlaceInfo;
import com.sync.algorithm.step1.VoteInfo;
import com.sync.algorithm.step3.DaySchedule;
import com.sync.algorithm.step3.ScheduledPlace;
import com.sync.algorithm.step3.SimpleTsp;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AlgorithmServiceTest {

    // ── 헬퍼 ────────────────────────────────────────────────────────────

    private static final double DEST_LAT = 37.5665;
    private static final double DEST_LNG = 126.9780;

    private GroupInfo group(TravelStyle style, int days, boolean isOverseas) {
        LocalDate start = LocalDate.of(2025, 7, 1);
        return new GroupInfo(1L, DEST_LAT, DEST_LNG, style, start,
                start.plusDays(days - 1), isOverseas, null, null);
    }

    private List<MemberInfo> members(int count) {
        List<MemberInfo> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) list.add(new MemberInfo(i, "MEMBER", true));
        return list;
    }

    private PlaceInfo place(long id, PlaceCategory cat, int density, double lat, double lng) {
        return new PlaceInfo(id, 1L, "P" + id, cat, density, 60, lat, lng);
    }

    private VoteInfo like(long placeId, long userId)    { return new VoteInfo(placeId, userId,  1); }
    private VoteInfo dislike(long placeId, long userId) { return new VoteInfo(placeId, userId, -1); }

    // ── 기본 파이프라인 ──────────────────────────────────────────────────

    @Test
    void K1_국내_기본_파이프라인() {
        // 4명, 1일, RELAXED
        // P1~P3: 4 likes 각각 (mainPool), P4: 1 like (altPool)
        List<PlaceInfo> places = List.of(
                place(1, PlaceCategory.CULTURE,  1, DEST_LAT,       DEST_LNG),
                place(2, PlaceCategory.NATURE,   1, DEST_LAT + 0.1, DEST_LNG),
                place(3, PlaceCategory.FOOD,     1, DEST_LAT,       DEST_LNG + 0.1),
                place(4, PlaceCategory.ACTIVITY, 1, DEST_LAT - 0.1, DEST_LNG)
        );
        List<VoteInfo> votes = new ArrayList<>();
        for (long uid = 1; uid <= 4; uid++) {
            votes.add(like(1, uid));
            votes.add(like(2, uid));
            votes.add(like(3, uid));
        }
        votes.add(like(4, 1));
        votes.add(dislike(4, 2));

        AlgorithmInput input = new AlgorithmInput(
                group(TravelStyle.RELAXED, 1, false),
                members(4), places, votes, null, Map.of());

        AlgorithmResult result = AlgorithmService.compute(input);

        // 1일 스케줄
        assertThat(result.step3Result().daySchedules()).hasSize(1);
        DaySchedule day1 = result.step3Result().daySchedules().get(0);
        assertThat(day1.day()).isEqualTo(1);

        // 3개 장소 배정 (P1·P2·P3, P4는 altPool)
        assertThat(day1.places()).hasSize(3);

        // 첫 장소는 dayStart, 이후 장소는 이전 endTime + 이동 시간 이후 시작
        List<ScheduledPlace> sp = day1.places();
        assertThat(sp.get(0).startTime()).isEqualTo(SimpleTsp.DEFAULT_DAY_START);
        for (int i = 1; i < sp.size(); i++) {
            assertThat(sp.get(i).startTime()).isAfterOrEqualTo(sp.get(i - 1).endTime());
        }

        // 국내 → 영업시간 위반 없음
        assertThat(sp).allMatch(p -> !p.openingHoursViolation());
    }

    @Test
    void altPool이_AlgorithmResult에_보존됨() {
        // P4: 1 like → altPool 진입 (4명 기준 threshold=2, altMin=1)
        List<PlaceInfo> places = List.of(
                place(1, PlaceCategory.CULTURE, 1, DEST_LAT, DEST_LNG),
                place(2, PlaceCategory.NATURE,  1, DEST_LAT, DEST_LNG),
                place(3, PlaceCategory.FOOD,    1, DEST_LAT, DEST_LNG),
                place(4, PlaceCategory.ACTIVITY,1, DEST_LAT, DEST_LNG)
        );
        List<VoteInfo> votes = new ArrayList<>();
        for (long uid = 1; uid <= 4; uid++) {
            votes.add(like(1, uid));
            votes.add(like(2, uid));
            votes.add(like(3, uid));
        }
        votes.add(like(4, 1));
        votes.add(dislike(4, 2));

        AlgorithmInput input = new AlgorithmInput(
                group(TravelStyle.RELAXED, 1, false),
                members(4), places, votes, null, Map.of());

        AlgorithmResult result = AlgorithmService.compute(input);

        assertThat(result.step1Result().altPool()).isNotEmpty();
        assertThat(result.step1Result().altPool().get(0).placeId()).isEqualTo(4L);
    }

    @Test
    void mainPool_비어있으면_K개의_빈_스케줄_반환() {
        // 모든 장소가 투표 미통과 → mainPool 비어있음
        List<PlaceInfo> places = List.of(
                place(1, PlaceCategory.CULTURE, 1, DEST_LAT, DEST_LNG)
        );
        List<VoteInfo> votes = List.of(dislike(1, 1));

        AlgorithmInput input = new AlgorithmInput(
                group(TravelStyle.RELAXED, 2, false),
                members(4), places, votes, null, Map.of());

        AlgorithmResult result = AlgorithmService.compute(input);

        assertThat(result.step3Result().daySchedules()).hasSize(2);
        assertThat(result.step3Result().daySchedules())
                .allMatch(ds -> ds.places().isEmpty());
        assertThat(result.step3Result().overflow()).isEmpty();
        assertThat(result.step1Result().altPool()).isEmpty();
    }

    // ── 커스텀 dayStartTime ──────────────────────────────────────────────

    @Test
    void 커스텀_dayStartTime이_스케줄에_반영됨() {
        List<PlaceInfo> places = List.of(
                place(1, PlaceCategory.CULTURE, 1, DEST_LAT, DEST_LNG)
        );
        List<VoteInfo> votes = List.of(like(1, 1), like(1, 2), like(1, 3));

        AlgorithmInput input = new AlgorithmInput(
                group(TravelStyle.RELAXED, 1, false),
                members(3), places, votes,
                LocalTime.of(8, 30), Map.of());

        AlgorithmResult result = AlgorithmService.compute(input);

        ScheduledPlace first = result.step3Result().daySchedules().get(0).places().get(0);
        assertThat(first.startTime()).isEqualTo(LocalTime.of(8, 30));
    }

    // ── K=2 다일차 ──────────────────────────────────────────────────────

    @Test
    void K2_결과에_2개_DaySchedule_반환() {
        // 6개 장소, 모두 충분한 투표
        // 2일 여행 → DaySchedule 2개
        List<PlaceInfo> places = new ArrayList<>();
        List<VoteInfo> votes = new ArrayList<>();
        double[] lats = {37.5, 37.6, 37.5, 37.6, 37.5, 37.6};
        double[] lngs = {127.0, 127.0, 127.1, 127.1, 127.2, 127.2};
        for (long id = 1; id <= 6; id++) {
            places.add(place(id, PlaceCategory.CULTURE, 1,
                    lats[(int)(id-1)], lngs[(int)(id-1)]));
            for (long uid = 1; uid <= 4; uid++) votes.add(like(id, uid));
        }

        AlgorithmInput input = new AlgorithmInput(
                group(TravelStyle.PACKED, 2, false),
                members(4), places, votes, null, Map.of());

        AlgorithmResult result = AlgorithmService.compute(input);

        assertThat(result.step3Result().daySchedules()).hasSize(2);
        // 전체 배정 장소 수 = 6 (overflow 없어야 함 — PACKED density=8/day, food=0)
        long total = result.step3Result().daySchedules().stream()
                .mapToLong(ds -> ds.places().size()).sum();
        assertThat(total).isEqualTo(6);
    }

    // ── 결정론성 ─────────────────────────────────────────────────────────

    @Test
    void 결정론성_같은_입력이면_항상_같은_출력() {
        List<PlaceInfo> places = List.of(
                place(1, PlaceCategory.CULTURE, 1, DEST_LAT,       DEST_LNG),
                place(2, PlaceCategory.NATURE,  1, DEST_LAT + 0.1, DEST_LNG + 0.1),
                place(3, PlaceCategory.FOOD,    1, DEST_LAT - 0.1, DEST_LNG - 0.1)
        );
        List<VoteInfo> votes = new ArrayList<>();
        for (long uid = 1; uid <= 4; uid++) {
            votes.add(like(1, uid));
            votes.add(like(2, uid));
            votes.add(like(3, uid));
        }

        AlgorithmInput input = new AlgorithmInput(
                group(TravelStyle.RELAXED, 1, false),
                members(4), places, votes, null, Map.of());

        AlgorithmResult r1 = AlgorithmService.compute(input);
        AlgorithmResult r2 = AlgorithmService.compute(input);

        assertThat(r1.step3Result().daySchedules())
                .isEqualTo(r2.step3Result().daySchedules());
    }
}
