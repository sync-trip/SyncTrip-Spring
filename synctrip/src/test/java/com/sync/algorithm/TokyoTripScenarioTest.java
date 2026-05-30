package com.sync.algorithm;

import com.sync.algorithm.step1.GroupInfo;
import com.sync.algorithm.step1.MemberInfo;
import com.sync.algorithm.step1.PlaceInfo;
import com.sync.algorithm.step1.VoteInfo;
import com.sync.algorithm.step3.DaySchedule;
import com.sync.algorithm.step3.OpeningHours;
import com.sync.algorithm.step3.ScheduledPlace;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 도쿄 3박4일 그룹 여행 시나리오 통합 테스트
 *
 * 시나리오 개요:
 *   - 그룹     : 4명 (리더 1인 + 일반 멤버 3인)
 *   - 여행지   : 도쿄 (일본) — isOverseas=true, 영업시간 체크 활성
 *   - 일정     : 2025-08-01 ~ 2025-08-04 (3박4일, K=4)
 *   - 여행스타일: PACKED (하루 density=8, FOOD 쿼터=2)
 *   - 후보 장소 : 20개
 *     ∙ mainPool 예상 17개 (like ≥ 2, priorityScore > 0, 밀도 예산 이내)
 *     ∙ altPool  예상  3개 (like = 1, voteScore > 0)
 *     ∙ 이상치   예상  1개 (닛코, 목적지에서 ~130km)
 *
 * 검증 항목:
 *   1) 전체 파이프라인 정상 실행 (K=4 DaySchedule 반환)
 *   2) 날짜별 FOOD 쿼터 준수 (≤ 2개/일)
 *   3) 중복 장소 없음 (같은 placeId가 여러 날에 배정되지 않음)
 *   4) 당일 시간 할당 연속성 (endTime[i] ≤ startTime[i+1])
 *   5) 이상치 마킹 (닛코 → isOutlierCandidate=true)
 *   6) 해외 영업시간 체크 (openingHoursViolation 필드 비정상 예외 없음)
 *   7) 결정론성 (동일 입력 → 동일 출력)
 *   8) 투표 기반 풀 분류 (mainPool/altPool 경계 정확성)
 */
class TokyoTripScenarioTest {

    // ── 도쿄 여행 기본 설정 ────────────────────────────────────────────────
    /** 도쿄 중심 좌표 (도쿄역 부근) */
    private static final double DEST_LAT  = 35.6762;
    private static final double DEST_LNG  = 139.6503;
    private static final LocalDate START  = LocalDate.of(2025, 8, 1);
    private static final LocalDate END    = LocalDate.of(2025, 8, 4); // K=4

    // ── 장소 ID 상수 (가독성용) ─────────────────────────────────────────
    // mainPool 예상 (like ≥ 2)
    private static final long P_SENSOJI       = 1L;   // 센소지          CULTURE  (4 likes)
    private static final long P_SKYTREE       = 2L;   // 도쿄 스카이트리 ACTIVITY (4 likes)
    private static final long P_SHIBUYA       = 3L;   // 시부야 스크램블 CULTURE  (4 likes)
    private static final long P_TEAMLAB       = 4L;   // 팀랩 보더리스   ACTIVITY (3 likes, 1 dislike)
    private static final long P_TSUKIJI       = 5L;   // 쓰키지 시장     FOOD     (4 likes)
    private static final long P_SHINJUKU_PARK = 6L;   // 신주쿠 교엔     NATURE   (3 likes)
    private static final long P_MEIJI         = 7L;   // 메이지 신궁     CULTURE  (3 likes)
    private static final long P_AKIHABARA     = 8L;   // 아키하바라      SHOPPING (3 likes)
    private static final long P_HARAJUKU      = 9L;   // 하라주쿠        SHOPPING (2 likes — threshold 경계)
    private static final long P_UENO          = 10L;  // 우에노 공원     NATURE   (3 likes)
    private static final long P_MUSEUM        = 11L;  // 도쿄 국립박물관 CULTURE  (2 likes — threshold 경계)
    private static final long P_RAMEN         = 13L;  // 라멘 이치란     FOOD     (4 likes)
    private static final long P_SUSHI         = 14L;  // 스시 긴자       FOOD     (3 likes)
    private static final long P_TOWER         = 16L;  // 도쿄 타워       CULTURE  (2 likes)
    private static final long P_ODAIBA        = 17L;  // 오다이바        ACTIVITY (2 likes)
    private static final long P_NIKKO         = 19L;  // 닛코 도쇼구     CULTURE  (2 likes, 이상치: ~130km)
    private static final long P_HAMARIKYU     = 20L;  // 하마리큐 정원   NATURE   (3 likes)

    // altPool 예상 (like = 1, voteScore > 0)
    private static final long P_ROPPONGI  = 12L;  // 롯폰기 힐즈    ACTIVITY
    private static final long P_IZAKAYA   = 15L;  // 이자카야 신주쿠 FOOD (P_RAMEN 인근 ~0.2km)
    private static final long P_GINZA_SHP = 18L;  // 긴자 쇼핑      SHOPPING

    // ── 장소 목록 빌더 ─────────────────────────────────────────────────────

    private static List<PlaceInfo> buildPlaces() {
        return List.of(
            // id  bookmarkBy  name              category                  density  duration  lat       lng
            // ── mainPool 예상 장소 ──────────────────────────────────────────────────────────────────
            new PlaceInfo(P_SENSOJI,       1L, "센소지",            PlaceCategory.CULTURE,   2, 90,  35.7147, 139.7967),
            new PlaceInfo(P_SKYTREE,       1L, "도쿄 스카이트리",   PlaceCategory.ACTIVITY,  2, 120, 35.7101, 139.8107),
            new PlaceInfo(P_SHIBUYA,       1L, "시부야 스크램블",   PlaceCategory.CULTURE,   1, 60,  35.6595, 139.7004),
            new PlaceInfo(P_TEAMLAB,       1L, "팀랩 보더리스",     PlaceCategory.ACTIVITY,  3, 180, 35.6249, 139.7750),
            new PlaceInfo(P_TSUKIJI,       1L, "쓰키지 시장",       PlaceCategory.FOOD,      1, 90,  35.6654, 139.7707),
            new PlaceInfo(P_SHINJUKU_PARK, 1L, "신주쿠 교엔",       PlaceCategory.NATURE,    1, 90,  35.6852, 139.7100),
            new PlaceInfo(P_MEIJI,         1L, "메이지 신궁",       PlaceCategory.CULTURE,   1, 90,  35.6763, 139.6993),
            new PlaceInfo(P_AKIHABARA,     1L, "아키하바라",        PlaceCategory.SHOPPING,  2, 120, 35.7023, 139.7745),
            new PlaceInfo(P_HARAJUKU,      1L, "하라주쿠",          PlaceCategory.SHOPPING,  1, 60,  35.6701, 139.7024),
            new PlaceInfo(P_UENO,          1L, "우에노 공원",       PlaceCategory.NATURE,    1, 60,  35.7141, 139.7741),
            new PlaceInfo(P_MUSEUM,        1L, "도쿄 국립박물관",   PlaceCategory.CULTURE,   2, 120, 35.7188, 139.7768),
            new PlaceInfo(P_RAMEN,         1L, "라멘 이치란 신주쿠", PlaceCategory.FOOD,     1, 60,  35.6886, 139.6941),
            new PlaceInfo(P_SUSHI,         1L, "스시 긴자",         PlaceCategory.FOOD,      1, 60,  35.6717, 139.7669),
            new PlaceInfo(P_TOWER,         1L, "도쿄 타워",         PlaceCategory.CULTURE,   2, 90,  35.6586, 139.7454),
            new PlaceInfo(P_ODAIBA,        1L, "오다이바 관람차",   PlaceCategory.ACTIVITY,  2, 90,  35.6248, 139.7750),
            new PlaceInfo(P_NIKKO,         1L, "닛코 도쇼구",       PlaceCategory.CULTURE,   3, 240, 36.7585, 139.5990), // ~130km 이상치
            new PlaceInfo(P_HAMARIKYU,     1L, "하마리큐 정원",     PlaceCategory.NATURE,    1, 90,  35.6600, 139.7648),
            // ── altPool 예상 장소 ────────────────────────────────────────────────────────────────────
            new PlaceInfo(P_ROPPONGI,  1L, "롯폰기 힐즈",    PlaceCategory.ACTIVITY,  2, 90,  35.6604, 139.7292),
            new PlaceInfo(P_IZAKAYA,   1L, "이자카야 신주쿠", PlaceCategory.FOOD,      1, 90,  35.6896, 139.6957), // P_RAMEN에서 ~0.2km
            new PlaceInfo(P_GINZA_SHP, 1L, "긴자 쇼핑거리",  PlaceCategory.SHOPPING,  2, 90,  35.6719, 139.7673)
        );
    }

    /**
     * 투표 설계 (4명: userId 1~4)
     *  - 4 likes: P1,P2,P3,P5,P13          → mainPool 확실
     *  - 3 likes: P4(+1dislike), P6,P7,P8,P10,P14,P20 → mainPool
     *  - 2 likes: P9,P11,P16,P17,P19       → mainPool (threshold=2 경계)
     *  - 1 like + dislike: P12,P18         → altPool (voteScore>0)
     *  - 1 like only: P15                  → altPool (voteScore>0)
     */
    private static List<VoteInfo> buildVotes() {
        List<VoteInfo> v = new ArrayList<>();

        // 4명 모두 LIKE
        for (long uid = 1; uid <= 4; uid++) {
            v.add(new VoteInfo(P_SENSOJI,  uid,  1));
            v.add(new VoteInfo(P_SKYTREE,  uid,  1));
            v.add(new VoteInfo(P_SHIBUYA,  uid,  1));
            v.add(new VoteInfo(P_TSUKIJI,  uid,  1));
            v.add(new VoteInfo(P_RAMEN,    uid,  1));
        }

        // 3명 LIKE (1명 제외 = no-vote, row 없음)
        for (long uid = 1; uid <= 3; uid++) {
            v.add(new VoteInfo(P_SHINJUKU_PARK, uid, 1));
            v.add(new VoteInfo(P_MEIJI,         uid, 1));
            v.add(new VoteInfo(P_AKIHABARA,     uid, 1));
            v.add(new VoteInfo(P_UENO,          uid, 1));
            v.add(new VoteInfo(P_SUSHI,         uid, 1));
            v.add(new VoteInfo(P_HAMARIKYU,     uid, 1));
        }

        // 3 LIKE + 1 DISLIKE → voteScore = (3/4)*2 - (1/4) = 1.25 > 0
        v.add(new VoteInfo(P_TEAMLAB, 1L,  1));
        v.add(new VoteInfo(P_TEAMLAB, 2L,  1));
        v.add(new VoteInfo(P_TEAMLAB, 3L,  1));
        v.add(new VoteInfo(P_TEAMLAB, 4L, -1));

        // 2 LIKE (threshold 경계 — like_count >= 2 로 mainPool 진입)
        v.add(new VoteInfo(P_HARAJUKU, 1L, 1));
        v.add(new VoteInfo(P_HARAJUKU, 2L, 1));
        v.add(new VoteInfo(P_MUSEUM,   1L, 1));
        v.add(new VoteInfo(P_MUSEUM,   2L, 1));
        v.add(new VoteInfo(P_TOWER,    1L, 1));
        v.add(new VoteInfo(P_TOWER,    2L, 1));
        v.add(new VoteInfo(P_ODAIBA,   1L, 1));
        v.add(new VoteInfo(P_ODAIBA,   2L, 1));
        v.add(new VoteInfo(P_NIKKO,    1L, 1));  // 닛코: mainPool이지만 이상치(130km)
        v.add(new VoteInfo(P_NIKKO,    2L, 1));

        // 1 LIKE + 1 DISLIKE → like=1(altPool 범위), voteScore=(1/2)*2-(1/2)=0.5>0 → altPool
        v.add(new VoteInfo(P_ROPPONGI,  1L,  1));
        v.add(new VoteInfo(P_ROPPONGI,  2L, -1));
        v.add(new VoteInfo(P_GINZA_SHP, 1L,  1));
        v.add(new VoteInfo(P_GINZA_SHP, 2L, -1));

        // 1 LIKE only → voteScore=(1/1)*2=2.0>0, like=1 → altPool
        v.add(new VoteInfo(P_IZAKAYA, 1L, 1));

        return v;
    }

    /** 해외 장소 영업시간 (도쿄 현지 기준) */
    private static Map<Long, OpeningHours> buildOpeningHours() {
        return Map.of(
            P_SKYTREE,       new OpeningHours(LocalTime.of(10, 0), LocalTime.of(22, 0)),
            P_TSUKIJI,       new OpeningHours(LocalTime.of(5,  0), LocalTime.of(14, 0)),
            P_TEAMLAB,       new OpeningHours(LocalTime.of(10, 0), LocalTime.of(21, 0)),
            P_SHINJUKU_PARK, new OpeningHours(LocalTime.of(9,  0), LocalTime.of(17, 30)),
            P_MUSEUM,        new OpeningHours(LocalTime.of(9, 30), LocalTime.of(17, 0)),
            P_HAMARIKYU,     new OpeningHours(LocalTime.of(9,  0), LocalTime.of(17, 0))
        );
    }

    /** 표준 도쿄 시나리오 AlgorithmInput 생성 */
    private static AlgorithmInput buildTokyoInput() {
        List<MemberInfo> members = List.of(
            new MemberInfo(1L, "LEADER", true),
            new MemberInfo(2L, "MEMBER", true),
            new MemberInfo(3L, "MEMBER", true),
            new MemberInfo(4L, "MEMBER", true)
        );
        GroupInfo group = new GroupInfo(
            1L, DEST_LAT, DEST_LNG,
            TravelStyle.PACKED,
            START, END,
            true  // 해외
        );
        return new AlgorithmInput(
            group, members, buildPlaces(), buildVotes(),
            LocalTime.of(9, 0),
            buildOpeningHours()
        );
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 1: 전체 파이프라인 정상 실행 — K=4 DaySchedule 반환
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void 도쿄_3박4일_전체_파이프라인_정상_실행() {
        AlgorithmResult result = AlgorithmService.compute(buildTokyoInput());

        List<DaySchedule> days = result.step3Result().daySchedules();

        // 3박4일 → K=4 일차
        assertThat(days).as("3박4일이므로 DaySchedule 수는 4개여야 함").hasSize(4);

        // 각 일차 번호는 1~4
        assertThat(days).extracting(DaySchedule::day)
            .containsExactlyInAnyOrder(1, 2, 3, 4);

        // mainPool이 17개이므로 총 배정 장소 수는 충분해야 함 (밀도 초과 overflow 고려 시 최소 10개)
        long totalScheduled = days.stream()
            .mapToLong(d -> d.places().size())
            .sum();
        assertThat(totalScheduled)
            .as("총 배정 장소 수가 너무 적음 (mainPool 17개 중 대부분 배정 예상)")
            .isGreaterThanOrEqualTo(10);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 2: 날짜별 FOOD 쿼터 준수 (PACKED = 하루 최대 2개)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void 날짜별_FOOD_쿼터_PACKED_하루_최대2개_준수() {
        AlgorithmResult result = AlgorithmService.compute(buildTokyoInput());

        for (DaySchedule day : result.step3Result().daySchedules()) {
            long foodCount = day.places().stream()
                .filter(p -> p.category() == PlaceCategory.FOOD)
                .count();
            assertThat(foodCount)
                .as("Day %d FOOD 개수(%d)가 PACKED 쿼터(2)를 초과함",
                    day.day(), foodCount)
                .isLessThanOrEqualTo(2);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 3: 중복 장소 없음 — 같은 장소가 여러 날에 배정되지 않아야 함
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void 날짜간_중복_장소_없음() {
        AlgorithmResult result = AlgorithmService.compute(buildTokyoInput());

        List<Long> allPlaceIds = result.step3Result().daySchedules().stream()
            .flatMap(d -> d.places().stream())
            .map(ScheduledPlace::placeId)
            .collect(Collectors.toList());

        Set<Long> unique = new HashSet<>(allPlaceIds);
        assertThat(unique.size())
            .as("중복 placeId 감지: 전체 %d개 중 %d개만 고유함", allPlaceIds.size(), unique.size())
            .isEqualTo(allPlaceIds.size());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 4: 당일 시간 할당 연속성 — endTime[i] ≤ startTime[i+1]
    // ══════════════════════════════════════════════════════════════════════
    @Test
    void 당일_시간_할당_순서_연속성() {
        AlgorithmResult result = AlgorithmService.compute(buildTokyoInput());

        for (DaySchedule day : result.step3Result().daySchedules()) {
            List<ScheduledPlace> places = day.places();
            for (int i = 1; i < places.size(); i++) {
                LocalTime prevEnd   = places.get(i - 1).endTime();
                LocalTime curStart  = places.get(i).startTime();
                assertThat(curStart)
                    .as("Day %d: 장소[%d].startTime(%s)이 장소[%d].endTime(%s)보다 앞섬",
                        day.day(), i, curStart, i - 1, prevEnd)
                    .isAfterOrEqualTo(prevEnd);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 5: 이상치 마킹 — 닛코(~130km)는 isOutlierCandidate=true 여야 함
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void 닛코_이상치_마킹_확인() {
        AlgorithmResult result = AlgorithmService.compute(buildTokyoInput());

        // 닛코가 mainPool에 포함되었는지 확인
        boolean nikkoInMainPool = result.step1Result().mainPool().stream()
            .anyMatch(p -> p.placeId() == P_NIKKO);
        assertThat(nikkoInMainPool)
            .as("닛코(P_NIKKO)가 mainPool에 포함되어야 함 (like=2 ≥ threshold=2)")
            .isTrue();

        // mainPool에서 닛코의 이상치 플래그 확인
        result.step1Result().mainPool().stream()
            .filter(p -> p.placeId() == P_NIKKO)
            .findFirst()
            .ifPresent(nikko -> {
                assertThat(nikko.distanceKm())
                    .as("닛코까지 거리는 30km를 초과해야 함 (실제: %.1fkm)", nikko.distanceKm())
                    .isGreaterThan(30.0);
                assertThat(nikko.isOutlierCandidate())
                    .as("닛코(%.1fkm)는 OUTLIER_DIST_KM(30km)을 초과하므로 isOutlierCandidate=true 여야 함",
                        nikko.distanceKm())
                    .isTrue();
            });

        // Step3 스케줄에 닛코가 포함된 경우 isOutlierCandidate 플래그가 전파되어야 함
        result.step3Result().daySchedules().stream()
            .flatMap(d -> d.places().stream())
            .filter(sp -> sp.placeId() == P_NIKKO)
            .forEach(sp ->
                assertThat(sp.isOutlierCandidate())
                    .as("Step3 스케줄의 닛코도 isOutlierCandidate=true 여야 함")
                    .isTrue()
            );
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 6: 해외 영업시간 체크 — isOverseas=true 시 예외 없이 필드 설정됨
    //         (국내는 항상 false; 해외는 위반 여부에 따라 true/false)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void 해외_영업시간_체크_필드_정상_설정() {
        AlgorithmResult result = AlgorithmService.compute(buildTokyoInput());

        // 예외 없이 실행 완료 + openingHoursViolation 필드가 모두 유효한 boolean 값을 가짐
        List<ScheduledPlace> allScheduled = result.step3Result().daySchedules().stream()
            .flatMap(d -> d.places().stream())
            .collect(Collectors.toList());

        assertThat(allScheduled).isNotEmpty();

        // 영업시간 정보가 없는 장소는 violation=false 여야 함
        long violationWithNoHours = allScheduled.stream()
            .filter(sp -> !buildOpeningHours().containsKey(sp.placeId()))
            .filter(ScheduledPlace::openingHoursViolation)
            .count();
        assertThat(violationWithNoHours)
            .as("영업시간 정보가 없는 장소에서 openingHoursViolation=true 가 발생하면 안 됨")
            .isZero();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 7: 결정론성 — 동일 입력은 반드시 동일 출력을 생성
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void 결정론성_동일_입력_동일_출력() {
        AlgorithmInput input = buildTokyoInput();

        AlgorithmResult r1 = AlgorithmService.compute(input);
        AlgorithmResult r2 = AlgorithmService.compute(input);

        assertThat(r1.step3Result().daySchedules())
            .as("동일 입력에 대해 두 번 실행한 결과가 달라선 안 됨")
            .isEqualTo(r2.step3Result().daySchedules());

        assertThat(r1.step1Result().mainPool())
            .as("Step1 mainPool도 결정론적이어야 함")
            .isEqualTo(r2.step1Result().mainPool());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 8: 투표 기반 풀 분류 — mainPool/altPool 경계값 검증
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void 투표_기반_풀_분류_검증() {
        AlgorithmResult result = AlgorithmService.compute(buildTokyoInput());

        Set<Long> mainPoolIds = result.step1Result().mainPool().stream()
            .map(p -> p.placeId())
            .collect(Collectors.toSet());
        Set<Long> altPoolIds = result.step1Result().altPool().stream()
            .map(p -> p.placeId())
            .collect(Collectors.toSet());

        // 4명, threshold = ceil(4×0.5) = 2
        // like=4인 장소는 반드시 mainPool
        assertThat(mainPoolIds).as("4명 all-LIKE 장소는 mainPool에 있어야 함")
            .contains(P_SENSOJI, P_SKYTREE, P_SHIBUYA, P_TSUKIJI, P_RAMEN);

        // like=2인 장소도 threshold=2 이상이므로 mainPool
        assertThat(mainPoolIds).as("like=2(=threshold) 장소도 mainPool에 있어야 함")
            .contains(P_HARAJUKU, P_MUSEUM, P_TOWER, P_ODAIBA, P_NIKKO);

        // like=1인 장소는 altPool (voteScore>0 조건 충족)
        assertThat(altPoolIds).as("like=1 장소는 altPool에 있어야 함")
            .contains(P_ROPPONGI, P_IZAKAYA, P_GINZA_SHP);

        // mainPool과 altPool은 disjoint
        Set<Long> overlap = new HashSet<>(mainPoolIds);
        overlap.retainAll(altPoolIds);
        assertThat(overlap).as("mainPool과 altPool에 동시에 존재하는 장소가 없어야 함").isEmpty();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 9: 각 일차 orderInDay 순서 일관성 — 1부터 연속 증가
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void 당일_장소_orderInDay_1부터_연속_증가() {
        AlgorithmResult result = AlgorithmService.compute(buildTokyoInput());

        for (DaySchedule day : result.step3Result().daySchedules()) {
            List<ScheduledPlace> places = day.places();
            for (int i = 0; i < places.size(); i++) {
                assertThat(places.get(i).orderInDay())
                    .as("Day %d: places[%d].orderInDay 는 %d 여야 함",
                        day.day(), i, i + 1)
                    .isEqualTo(i + 1);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 11: 첫 장소 시작 시간 — dayStartTime(09:00) 적용 확인
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void 각_일차_첫_장소_시작시간이_09시() {
        AlgorithmResult result = AlgorithmService.compute(buildTokyoInput());

        for (DaySchedule day : result.step3Result().daySchedules()) {
            if (day.places().isEmpty()) continue;
            assertThat(day.places().get(0).startTime())
                .as("Day %d: 첫 장소 시작 시간은 dayStartTime(09:00)이어야 함", day.day())
                .isEqualTo(LocalTime.of(9, 0));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 12: altPool 보존 — PlanB용 altPool이 AlgorithmResult에 담겨있어야 함
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void altPool이_AlgorithmResult에_보존됨() {
        AlgorithmResult result = AlgorithmService.compute(buildTokyoInput());

        assertThat(result.step1Result().altPool())
            .as("altPool은 비어있으면 안 됨 (P_ROPPONGI, P_IZAKAYA, P_GINZA_SHP 예상)")
            .isNotEmpty();

        Set<Long> altIds = result.step1Result().altPool().stream()
            .map(p -> p.placeId())
            .collect(Collectors.toSet());

        assertThat(altIds)
            .as("altPool에는 like=1이고 voteScore>0인 장소가 포함되어야 함")
            .contains(P_ROPPONGI, P_IZAKAYA, P_GINZA_SHP);
    }
}
