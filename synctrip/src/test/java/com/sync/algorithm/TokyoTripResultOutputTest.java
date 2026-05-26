package com.sync.algorithm;

import com.sync.algorithm.planb.PlanBCandidate;
import com.sync.algorithm.planb.PlanBInput;
import com.sync.algorithm.planb.PlanBRecommender;
import com.sync.algorithm.step1.AltPoolPlace;
import com.sync.algorithm.step1.GroupInfo;
import com.sync.algorithm.step1.MainPoolPlace;
import com.sync.algorithm.step1.MemberInfo;
import com.sync.algorithm.step1.PlaceInfo;
import com.sync.algorithm.step1.Step1Meta;
import com.sync.algorithm.step1.VoteInfo;
import com.sync.algorithm.step3.DaySchedule;
import com.sync.algorithm.step3.OpeningHours;
import com.sync.algorithm.step3.ScheduledPlace;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 도쿄 3박4일 알고리즘 실제 출력값 출력 테스트
 * - 논문 표 작성용: Step1 풀 분류 결과 / Step3 일정표 / Plan B 추천 결과
 * - 실행 후 build/test-results/test/TEST-*.xml 의 <system-out> 또는
 *   아래 PowerShell 명령으로 출력 확인:
 *     .\gradlew.bat test --tests "com.sync.algorithm.TokyoTripResultOutputTest" --no-daemon
 *     [xml]$x = Get-Content build/test-results/test/TEST-com.sync.algorithm.TokyoTripResultOutputTest.xml -Encoding UTF8
 *     $x.testsuite.'system-out'
 */
class TokyoTripResultOutputTest {

    // ── 장소 ID 상수 ─────────────────────────────────────────────────────
    private static final long P_SENSOJI       = 1L;
    private static final long P_SKYTREE       = 2L;
    private static final long P_SHIBUYA       = 3L;
    private static final long P_TEAMLAB       = 4L;
    private static final long P_TSUKIJI       = 5L;
    private static final long P_SHINJUKU_PARK = 6L;
    private static final long P_MEIJI         = 7L;
    private static final long P_AKIHABARA     = 8L;
    private static final long P_HARAJUKU      = 9L;
    private static final long P_UENO          = 10L;
    private static final long P_MUSEUM        = 11L;
    private static final long P_ROPPONGI      = 12L;
    private static final long P_RAMEN         = 13L;
    private static final long P_SUSHI         = 14L;
    private static final long P_IZAKAYA       = 15L;
    private static final long P_TOWER         = 16L;
    private static final long P_ODAIBA        = 17L;
    private static final long P_GINZA_SHP     = 18L;
    private static final long P_NIKKO         = 19L;
    private static final long P_HAMARIKYU     = 20L;

    private static final double DEST_LAT = 35.6762;
    private static final double DEST_LNG = 139.6503;

    // ── 장소명 조회 맵 ──────────────────────────────────────────────────
    private static final Map<Long, PlaceInfo> PLACE_MAP;
    static {
        List<PlaceInfo> list = List.of(
            new PlaceInfo(P_SENSOJI,       1L, "센소지",             PlaceCategory.CULTURE,  2, 90,  35.7147, 139.7967),
            new PlaceInfo(P_SKYTREE,       1L, "도쿄 스카이트리",    PlaceCategory.ACTIVITY, 2, 120, 35.7101, 139.8107),
            new PlaceInfo(P_SHIBUYA,       1L, "시부야 스크램블",    PlaceCategory.CULTURE,  1, 60,  35.6595, 139.7004),
            new PlaceInfo(P_TEAMLAB,       1L, "팀랩 보더리스",      PlaceCategory.ACTIVITY, 3, 180, 35.6249, 139.7750),
            new PlaceInfo(P_TSUKIJI,       1L, "쓰키지 시장",        PlaceCategory.FOOD,     1, 90,  35.6654, 139.7707),
            new PlaceInfo(P_SHINJUKU_PARK, 1L, "신주쿠 교엔",        PlaceCategory.NATURE,   1, 90,  35.6852, 139.7100),
            new PlaceInfo(P_MEIJI,         1L, "메이지 신궁",        PlaceCategory.CULTURE,  1, 90,  35.6763, 139.6993),
            new PlaceInfo(P_AKIHABARA,     1L, "아키하바라",         PlaceCategory.SHOPPING, 2, 120, 35.7023, 139.7745),
            new PlaceInfo(P_HARAJUKU,      1L, "하라주쿠",           PlaceCategory.SHOPPING, 1, 60,  35.6701, 139.7024),
            new PlaceInfo(P_UENO,          1L, "우에노 공원",        PlaceCategory.NATURE,   1, 60,  35.7141, 139.7741),
            new PlaceInfo(P_MUSEUM,        1L, "도쿄 국립박물관",    PlaceCategory.CULTURE,  2, 120, 35.7188, 139.7768),
            new PlaceInfo(P_ROPPONGI,      1L, "롯폰기 힐즈",        PlaceCategory.ACTIVITY, 2, 90,  35.6604, 139.7292),
            new PlaceInfo(P_RAMEN,         1L, "라멘 이치란 신주쿠", PlaceCategory.FOOD,     1, 60,  35.6886, 139.6941),
            new PlaceInfo(P_SUSHI,         1L, "스시 긴자",          PlaceCategory.FOOD,     1, 60,  35.6717, 139.7669),
            new PlaceInfo(P_IZAKAYA,       1L, "이자카야 신주쿠",    PlaceCategory.FOOD,     1, 90,  35.6896, 139.6957),
            new PlaceInfo(P_TOWER,         1L, "도쿄 타워",          PlaceCategory.CULTURE,  2, 90,  35.6586, 139.7454),
            new PlaceInfo(P_ODAIBA,        1L, "오다이바 관람차",    PlaceCategory.ACTIVITY, 2, 90,  35.6248, 139.7750),
            new PlaceInfo(P_GINZA_SHP,     1L, "긴자 쇼핑거리",      PlaceCategory.SHOPPING, 2, 90,  35.6719, 139.7673),
            new PlaceInfo(P_NIKKO,         1L, "닛코 도쇼구",        PlaceCategory.CULTURE,  3, 240, 36.7585, 139.5990),
            new PlaceInfo(P_HAMARIKYU,     1L, "하마리큐 정원",      PlaceCategory.NATURE,   1, 90,  35.6600, 139.7648)
        );
        PLACE_MAP = new LinkedHashMap<>();
        list.forEach(p -> PLACE_MAP.put(p.placeId(), p));
    }

    private String name(long id)  { return PLACE_MAP.get(id).name(); }
    private String cat(long id)   { return PLACE_MAP.get(id).category().name(); }

    // ── 데이터 빌더 (TokyoTripScenarioTest와 동일) ───────────────────────

    private static AlgorithmInput buildInput() {
        List<MemberInfo> members = List.of(
            new MemberInfo(1L, "LEADER", true), new MemberInfo(2L, "MEMBER", true),
            new MemberInfo(3L, "MEMBER", true), new MemberInfo(4L, "MEMBER", true));
        GroupInfo group = new GroupInfo(1L, DEST_LAT, DEST_LNG, TravelStyle.PACKED,
            LocalDate.of(2025, 8, 1), LocalDate.of(2025, 8, 4), true);

        List<PlaceInfo> places = new ArrayList<>(PLACE_MAP.values());

        List<VoteInfo> votes = new ArrayList<>();
        for (long uid = 1; uid <= 4; uid++) {
            for (long pid : new long[]{P_SENSOJI, P_SKYTREE, P_SHIBUYA, P_TSUKIJI, P_RAMEN})
                votes.add(new VoteInfo(pid, uid, 1));
        }
        for (long uid = 1; uid <= 3; uid++) {
            for (long pid : new long[]{P_SHINJUKU_PARK, P_MEIJI, P_AKIHABARA,
                                        P_UENO, P_SUSHI, P_HAMARIKYU})
                votes.add(new VoteInfo(pid, uid, 1));
        }
        votes.addAll(List.of(
            new VoteInfo(P_TEAMLAB,    1L,  1), new VoteInfo(P_TEAMLAB,    2L,  1),
            new VoteInfo(P_TEAMLAB,    3L,  1), new VoteInfo(P_TEAMLAB,    4L, -1),
            new VoteInfo(P_HARAJUKU,   1L,  1), new VoteInfo(P_HARAJUKU,   2L,  1),
            new VoteInfo(P_MUSEUM,     1L,  1), new VoteInfo(P_MUSEUM,     2L,  1),
            new VoteInfo(P_TOWER,      1L,  1), new VoteInfo(P_TOWER,      2L,  1),
            new VoteInfo(P_ODAIBA,     1L,  1), new VoteInfo(P_ODAIBA,     2L,  1),
            new VoteInfo(P_NIKKO,      1L,  1), new VoteInfo(P_NIKKO,      2L,  1),
            new VoteInfo(P_ROPPONGI,   1L,  1), new VoteInfo(P_ROPPONGI,   2L, -1),
            new VoteInfo(P_GINZA_SHP,  1L,  1), new VoteInfo(P_GINZA_SHP,  2L, -1),
            new VoteInfo(P_IZAKAYA,    1L,  1)
        ));

        Map<Long, OpeningHours> hours = Map.of(
            P_SKYTREE,       new OpeningHours(LocalTime.of(10, 0), LocalTime.of(22, 0)),
            P_TSUKIJI,       new OpeningHours(LocalTime.of( 5, 0), LocalTime.of(14, 0)),
            P_TEAMLAB,       new OpeningHours(LocalTime.of(10, 0), LocalTime.of(21, 0)),
            P_SHINJUKU_PARK, new OpeningHours(LocalTime.of( 9, 0), LocalTime.of(17, 30)),
            P_MUSEUM,        new OpeningHours(LocalTime.of( 9,30), LocalTime.of(17, 0)),
            P_HAMARIKYU,     new OpeningHours(LocalTime.of( 9, 0), LocalTime.of(17, 0))
        );

        return new AlgorithmInput(group, members, places, votes, LocalTime.of(9, 0), hours);
    }

    // ── 출력 헬퍼 ────────────────────────────────────────────────────────

    private static void line(char c, int n) {
        System.out.println(String.valueOf(c).repeat(n));
    }
    private static void hr()  { line('─', 90); }
    private static void dhr() { line('═', 90); }

    // ══════════════════════════════════════════════════════════════════════
    // 논문용 알고리즘 전체 출력
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void 논문용_알고리즘_전체_출력() {
        AlgorithmResult result = AlgorithmService.compute(buildInput());
        Step1Meta meta = result.step1Result().meta();

        // ── 헤더 ──────────────────────────────────────────────────────────
        dhr();
        System.out.println("  SyncTrip 알고리즘 실행 결과 — 도쿄 3박4일 그룹 여행 시나리오");
        System.out.println("  그룹: 4명 (리더 1 + 멤버 3) | 스타일: PACKED | 해외: true");
        System.out.printf ("  K=%d일 | threshold=%d | FOOD쿼터=%d/일 | density한도=%d/일%n",
            meta.K(), meta.passedThreshold(), meta.foodPerDayQuota(), meta.densityLimit());
        dhr();

        // ── 표1: Step1 mainPool ───────────────────────────────────────────
        System.out.println();
        System.out.println("  [표1] Step1 — 투표 가중치 기반 mainPool 장소 우선순위 (총 "
            + result.step1Result().mainPool().size() + "개)");
        System.out.println("  ※ 우선순위점수 = 투표점수 × 0.7 - 정규거리 × 0.3");
        hr();
        System.out.printf("  %-4s  %-20s  %-8s  %4s  %4s  %6s  %8s  %8s  %4s%n",
            "순위", "장소명", "카테고리", "LIKE", "DIS", "투표점수", "거리(km)", "우선순위", "이상치");
        hr();

        int rank = 1;
        for (MainPoolPlace p : result.step1Result().mainPool()) {
            System.out.printf("  %-4d  %-20s  %-8s  %4d  %4d  %6.3f  %8.2f  %8.4f  %4s%n",
                rank++,
                name(p.placeId()),
                cat(p.placeId()),
                p.likeCount(),
                p.dislikeCount(),
                p.voteScore(),
                p.distanceKm(),
                p.priorityScore(),
                p.isOutlierCandidate() ? "★" : "-");
        }
        hr();
        System.out.println("  ★ = 이상치 (목적지에서 30km 초과)");

        // ── 표2: Step1 altPool ────────────────────────────────────────────
        System.out.println();
        System.out.println("  [표2] Step1 — altPool 대기 장소 (총 "
            + result.step1Result().altPool().size() + "개)");
        System.out.println("  ※ LIKE 수가 threshold 미만이지만 voteScore > 0 인 후보군");
        hr();
        System.out.printf("  %-4s  %-20s  %-8s  %4s  %6s  %8s%n",
            "순위", "장소명", "카테고리", "LIKE", "투표점수", "우선순위");
        hr();
        for (AltPoolPlace p : result.step1Result().altPool()) {
            System.out.printf("  %-4d  %-20s  %-8s  %4d  %6.3f  %8.4f%n",
                p.altRank(),
                name(p.placeId()),
                cat(p.placeId()),
                p.likeCount(),
                p.voteScore(),
                p.priorityScore());
        }
        hr();

        // ── 표3: Step3 일정표 ─────────────────────────────────────────────
        System.out.println();
        System.out.println("  [표3] Step3 — K-Means 군집화 + TSP 기반 최적 여행 일정");
        System.out.println("  ※ 시작 시간 09:00 | 이동속도 25km/h 가정 | 해외 영업시간 체크 활성");
        hr();
        System.out.printf("  %-4s  %-3s  %-20s  %-8s  %6s  %6s  %6s  %4s  %6s%n",
            "일차", "순서", "장소명", "카테고리", "시작", "종료", "체류분", "이상치", "영업위반");
        hr();

        LocalDate baseDate = LocalDate.of(2025, 8, 1);
        for (DaySchedule day : result.step3Result().daySchedules()) {
            if (day.places().isEmpty()) {
                System.out.printf("  %d일차 (%s)  — 배정 장소 없음%n",
                    day.day(), baseDate.plusDays(day.day() - 1));
                continue;
            }
            System.out.printf("  ── %d일차 (%s) ──%n",
                day.day(), baseDate.plusDays(day.day() - 1));
            for (ScheduledPlace sp : day.places()) {
                System.out.printf("  %-4d  %-3d  %-20s  %-8s  %6s  %6s  %6d  %4s  %6s%n",
                    sp.day(),
                    sp.orderInDay(),
                    name(sp.placeId()),
                    sp.category().name(),
                    sp.startTime(),
                    sp.endTime(),
                    sp.estimatedDuration(),
                    sp.isOutlierCandidate()       ? "★" : "-",
                    sp.openingHoursViolation()    ? "위반" : "-");
            }
        }
        hr();

        // ── 표4: overflow ─────────────────────────────────────────────────
        List<?> overflow = result.step3Result().overflow();
        System.out.println();
        System.out.printf("  [표4] Step2/3 overflow (밀도 초과 탈락) — 총 %d개%n", overflow.size());
        hr();
        if (overflow.isEmpty()) {
            System.out.println("  없음 (전체 mainPool 장소가 일정에 배정됨)");
        } else {
            overflow.forEach(o -> {
                MainPoolPlace mp = (MainPoolPlace) o;
                System.out.printf("  %-20s  %-8s  우선순위=%.4f%n",
                    name(mp.placeId()), cat(mp.placeId()), mp.priorityScore());
            });
        }
        hr();

        // ── 표5: Plan B 추천 ─────────────────────────────────────────────
        System.out.println();
        System.out.println("  [표5] Plan B — '라멘 이치란 신주쿠' 대체 장소 추천");
        System.out.println("  ※ 같은 카테고리(FOOD), 대상 장소에서 1km 이내 후보");
        hr();
        System.out.printf("  %-4s  %-20s  %-8s  %8s  %8s  %6s%n",
            "순위", "장소명", "카테고리", "추천점수", "거리(km)", "출처");
        hr();

        PlanBInput planBInput = new PlanBInput(
            result.step3Result(),
            result.step1Result().altPool(),
            new ArrayList<>(PLACE_MAP.values()),
            P_RAMEN);
        List<PlanBCandidate> recs = PlanBRecommender.recommend(planBInput).recommendations();

        if (recs.isEmpty()) {
            System.out.println("  추천 후보 없음");
        } else {
            int i = 1;
            for (PlanBCandidate c : recs) {
                System.out.printf("  %-4d  %-20s  %-8s  %8.4f  %8.3f  %6s%n",
                    i++,
                    name(c.placeId()),
                    c.category().name(),
                    c.recommendScore(),
                    c.distanceKmToTarget(),
                    c.fromOverflow() ? "overflow" : "altPool");
            }
        }
        hr();

        // ── 요약 통계 ─────────────────────────────────────────────────────
        System.out.println();
        System.out.println("  [요약]");
        long total = result.step3Result().daySchedules().stream()
            .mapToLong(d -> d.places().size()).sum();
        System.out.printf("  ∙ mainPool 장소 수      : %d개%n", result.step1Result().mainPool().size());
        System.out.printf("  ∙ altPool 장소 수       : %d개%n", result.step1Result().altPool().size());
        System.out.printf("  ∙ 총 배정 장소 수       : %d개%n", total);
        System.out.printf("  ∙ overflow (미배정)     : %d개%n", overflow.size());
        System.out.printf("  ∙ Plan B 추천 후보      : %d개%n", recs.size());
        long outliers = result.step3Result().daySchedules().stream()
            .flatMap(d -> d.places().stream())
            .filter(ScheduledPlace::isOutlierCandidate).count();
        System.out.printf("  ∙ 이상치 마킹 장소      : %d개%n", outliers);
        long violations = result.step3Result().daySchedules().stream()
            .flatMap(d -> d.places().stream())
            .filter(ScheduledPlace::openingHoursViolation).count();
        System.out.printf("  ∙ 영업시간 위반 장소    : %d개%n", violations);
        dhr();
    }
}
