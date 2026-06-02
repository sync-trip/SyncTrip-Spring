package com.sync.algorithm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sync.algorithm.step1.GroupInfo;
import com.sync.algorithm.step1.MemberInfo;
import com.sync.algorithm.step1.PlaceInfo;
import com.sync.algorithm.step1.VoteInfo;
import com.sync.algorithm.step1.AltPoolPlace;
import com.sync.algorithm.step1.MainPoolPlace;
import com.sync.algorithm.step2.AssignedPlace;
import com.sync.algorithm.step2.DayGroup;
import com.sync.algorithm.step3.DaySchedule;
import com.sync.algorithm.step3.OpeningHours;
import com.sync.algorithm.step3.ScheduledPlace;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 발표용 시각화 데이터 추출 테스트 (평가 X — 산출물 생성 전용).
 *
 * 실제 AlgorithmService 파이프라인을 도쿄 시나리오로 1회 실행한 뒤,
 * 단계별(후보 → Step1 정제 → Step2 군집 → Step3 동선) 결과를
 * evaluation/synctrip_pipeline.json 으로 직렬화한다.
 *
 * HTML 애니메이션은 이 JSON을 "재생"만 한다 — JS에서 알고리즘을 재구현하지 않으므로
 * 화면에 보이는 결과가 Java 실제 출력과 100% 일치한다.
 *
 * 추가로 방안 A(동선 효율) 지표를 함께 계산해 담는다:
 *   - ours    : Step3가 확정한 NN-TSP 동선의 총 이동거리(km)
 *   - naive   : 같은 장소를 우선순위(priorityScore) 내림차순으로 방문할 때의 거리
 *   - optimal : 완전탐색(브루트포스)으로 구한 이론적 최단 거리 (장소 ≤ 9개일 때만)
 */
class AlgorithmVizExportTest {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    // ── 도쿄 시나리오 좌표/날짜 (TokyoTripScenarioTest 기준) ──────────────────
    private static final double DEST_LAT = 35.6762;
    private static final double DEST_LNG = 139.6503;
    private static final LocalDate START = LocalDate.of(2025, 8, 1);
    private static final LocalDate END   = LocalDate.of(2025, 8, 4); // K=4

    @Test
    void 도쿄_시나리오_파이프라인_JSON_추출() throws Exception {
        List<PlaceInfo> candidates = buildPlaces();
        AlgorithmInput input = buildTokyoInput(candidates);
        AlgorithmResult result = AlgorithmService.compute(input);

        // placeId → 후보 정보 (이름/좌표/카테고리는 PlaceInfo에만 있음)
        Map<Long, PlaceInfo> byId = new LinkedHashMap<>();
        for (PlaceInfo p : candidates) byId.put(p.placeId(), p);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("scenario", "도쿄 4명 3박4일 (PACKED)");
        root.put("destination", Map.of("lat", DEST_LAT, "lng", DEST_LNG));
        root.put("K", result.step1Result().meta().K());

        // ── Stage 0: 후보 장소 전체 ──────────────────────────────────────────
        List<Map<String, Object>> candJson = new ArrayList<>();
        for (PlaceInfo p : candidates) candJson.add(placeBase(p));
        root.put("candidates", candJson);

        // ── Stage 1: Step1 정제 (mainPool / altPool / dropped) ────────────────
        Set<Long> mainIds = new LinkedHashSet<>();
        List<Map<String, Object>> mainJson = new ArrayList<>();
        for (MainPoolPlace m : result.step1Result().mainPool()) {
            mainIds.add(m.placeId());
            Map<String, Object> o = placeBase(byId.get(m.placeId()));
            o.put("priorityScore", round(m.priorityScore(), 4));
            o.put("voteScore", round(m.voteScore(), 4));
            o.put("likeCount", m.likeCount());
            o.put("dislikeCount", m.dislikeCount());
            o.put("distanceKm", round(m.distanceKm(), 2));
            o.put("isOutlier", m.isOutlierCandidate());
            mainJson.add(o);
        }

        Set<Long> altIds = new LinkedHashSet<>();
        List<Map<String, Object>> altJson = new ArrayList<>();
        for (AltPoolPlace a : result.step1Result().altPool()) {
            altIds.add(a.placeId());
            Map<String, Object> o = placeBase(byId.get(a.placeId()));
            o.put("priorityScore", round(a.priorityScore(), 4));
            o.put("voteScore", round(a.voteScore(), 4));
            o.put("likeCount", a.likeCount());
            o.put("altRank", a.altRank());
            altJson.add(o);
        }

        // dropped = 후보 − mainPool − altPool (투표 미달로 완전 탈락)
        List<Map<String, Object>> droppedJson = new ArrayList<>();
        for (PlaceInfo p : candidates) {
            if (!mainIds.contains(p.placeId()) && !altIds.contains(p.placeId())) {
                droppedJson.add(placeBase(p));
            }
        }

        Map<String, Object> step1 = new LinkedHashMap<>();
        step1.put("mainPool", mainJson);
        step1.put("altPool", altJson);
        step1.put("dropped", droppedJson);
        step1.put("passedThreshold", result.step1Result().meta().passedThreshold());
        root.put("step1", step1);

        // ── Stage 2: Step2 군집 (날짜별 배분) ─────────────────────────────────
        // AlgorithmService 내부 Step2 결과는 Step3 입력으로 흡수되므로,
        // Step3 daySchedules의 (day, placeId) 구성으로 군집을 역추적한다.
        List<Map<String, Object>> step2Days = new ArrayList<>();
        for (DaySchedule ds : result.step3Result().daySchedules()) {
            List<Map<String, Object>> places = new ArrayList<>();
            for (ScheduledPlace sp : ds.places()) {
                Map<String, Object> o = placeBase(byId.get(sp.placeId()));
                o.put("priorityScore", round(sp.priorityScore(), 4));
                places.add(o);
            }
            Map<String, Object> dayObj = new LinkedHashMap<>();
            dayObj.put("day", ds.day());
            dayObj.put("places", places);
            step2Days.add(dayObj);
        }
        root.put("step2", Map.of("dayGroups", step2Days));

        // ── Stage 3: Step3 동선 + 시간 + 경고 배지 ────────────────────────────
        List<Map<String, Object>> step3Days = new ArrayList<>();

        // 방안 A용 좌표 수집:
        //   kmeansDayPts : K-Means가 묶은 날짜별 좌표 (우리 알고리즘의 배분)
        //   allPts       : 전체 배정 좌표 {lat, lng, priorityScore} — Round-Robin 재배분용
        List<List<double[]>> kmeansDayPts = new ArrayList<>();
        List<double[]> allPts = new ArrayList<>();

        for (DaySchedule ds : result.step3Result().daySchedules()) {
            List<Map<String, Object>> places = new ArrayList<>();
            List<double[]> orderedPts = new ArrayList<>();   // Step3 확정 순서

            for (ScheduledPlace sp : ds.places()) {
                PlaceInfo base = byId.get(sp.placeId());
                Map<String, Object> o = placeBase(base);
                o.put("order", sp.orderInDay());
                o.put("start", sp.startTime().format(HHMM));
                o.put("end", sp.endTime().format(HHMM));
                o.put("durationMin", sp.estimatedDuration());
                o.put("priorityScore", round(sp.priorityScore(), 4));
                o.put("isOutlier", sp.isOutlierCandidate());
                Map<String, Object> flags = new LinkedHashMap<>();
                flags.put("openingHoursViolation", sp.openingHoursViolation());
                flags.put("mealWindowViolation", sp.mealWindowViolation());
                flags.put("lateSchedule", sp.lateSchedule());
                flags.put("openingHoursUnverified", sp.openingHoursUnverified());
                o.put("flags", flags);
                places.add(o);
                orderedPts.add(new double[]{base.latitude(), base.longitude()});
            }

            Map<String, Object> dayObj = new LinkedHashMap<>();
            dayObj.put("day", ds.day());
            dayObj.put("dayOverloaded", ds.dayOverloaded());
            dayObj.put("places", places);
            step3Days.add(dayObj);

            // 방안 A용 좌표 수집 (K-Means 날짜별 묶음 + 전체)
            List<double[]> dayCoords = new ArrayList<>();
            for (ScheduledPlace sp : ds.places()) {
                PlaceInfo base = byId.get(sp.placeId());
                dayCoords.add(new double[]{base.latitude(), base.longitude()});
                allPts.add(new double[]{base.latitude(), base.longitude(), sp.priorityScore()});
            }
            kmeansDayPts.add(dayCoords);
        }
        root.put("step3", Map.of("daySchedules", step3Days));

        // ════════════════════════════════════════════════════════════════════
        // 방안 A: 동선 효율 — "가까운 장소를 같은 날에 묶는가"를 총 이동거리로 측정
        //
        // ① clustering : K-Means 날짜 배분 vs Round-Robin 배분의 총 이동거리.
        //    라우팅(하루 내 방문순서)은 양쪽 모두 "최적해(완전탐색)"로 고정해
        //    순수하게 군집 품질 차이만 비교한다. → 알고리즘의 핵심 가치.
        // ② routingQuality : K-Means 날짜에 대해 NN-TSP(실제 방식) vs 최적해.
        //    Step3의 Nearest Neighbor 휴리스틱이 최적에 얼마나 근접하는지.
        // ════════════════════════════════════════════════════════════════════
        int K = result.step1Result().meta().K();

        // ── ① K-Means vs Round-Robin (총 최적 이동거리) ──────────────────────
        List<Map<String, Object>> clusterPerDay = new ArrayList<>();
        double kmeansTotal = 0;
        for (int d = 0; d < kmeansDayPts.size(); d++) {
            List<double[]> pts = kmeansDayPts.get(d);
            double opt = pts.size() <= 1 ? 0.0 : minPathKm(stripPrio(pts), null, 0.0);
            kmeansTotal += opt;
            clusterPerDay.add(orderedMap("day", d + 1, "placeCount", pts.size(), "kmeansKm", round(opt, 2)));
        }

        // Round-Robin: 전체 장소를 우선순위 내림차순으로 K개 날짜에 순환 배정
        List<double[]> rr = new ArrayList<>(allPts);
        rr.sort((a, b) -> Double.compare(b[2], a[2]));   // priorityScore desc
        List<List<double[]>> rrDays = new ArrayList<>();
        for (int k = 0; k < K; k++) rrDays.add(new ArrayList<>());
        for (int i = 0; i < rr.size(); i++) {
            rrDays.get(i % K).add(new double[]{rr.get(i)[0], rr.get(i)[1]});
        }
        double rrTotal = 0;
        for (int k = 0; k < K; k++) {
            List<double[]> pts = rrDays.get(k);
            double opt = pts.size() <= 1 ? 0.0 : minPathKm(pts, null, 0.0);
            rrTotal += opt;
            // clusterPerDay[k]에 roundRobin 값 추가
            if (k < clusterPerDay.size()) clusterPerDay.get(k).put("roundRobinKm", round(opt, 2));
        }

        Map<String, Object> clustering = new LinkedHashMap<>();
        clustering.put("perDay", clusterPerDay);
        clustering.put("kmeansTotalKm", round(kmeansTotal, 2));
        clustering.put("roundRobinTotalKm", round(rrTotal, 2));
        // 단축률: Round-Robin 대비 K-Means가 얼마나 짧은가 (%)
        clustering.put("savingPct", rrTotal > 0 ? round((1 - kmeansTotal / rrTotal) * 100, 1) : null);

        // ── ② NN-TSP(실제) vs 최적해 — 라우팅 휴리스틱 품질 ──────────────────
        List<Map<String, Object>> routingPerDay = new ArrayList<>();
        double nnTotal = 0, nnOptTotal = 0;
        for (int d = 0; d < kmeansDayPts.size(); d++) {
            List<double[]> pts = stripPrio(kmeansDayPts.get(d));
            double nn  = nnRouteKm(pts);
            double opt = pts.size() <= 1 ? 0.0 : minPathKm(pts, null, 0.0);
            nnTotal += nn;
            nnOptTotal += opt;
            routingPerDay.add(orderedMap("day", d + 1, "nnKm", round(nn, 2), "optimalKm", round(opt, 2)));
        }
        Map<String, Object> routing = new LinkedHashMap<>();
        routing.put("perDay", routingPerDay);
        routing.put("nnTotalKm", round(nnTotal, 2));
        routing.put("optimalTotalKm", round(nnOptTotal, 2));
        // 최적 대비 NN이 몇 % 더 긴가 (작을수록 좋음, 0%면 최적과 동일)
        routing.put("gapToOptimalPct", nnOptTotal > 0 ? round((nnTotal / nnOptTotal - 1) * 100, 1) : null);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("clustering", clustering);
        metrics.put("routingQuality", routing);
        root.put("travelDistanceMetrics", metrics);

        // ── 직렬화 ───────────────────────────────────────────────────────────
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);

        Path base = Paths.get(System.getProperty("user.dir"));
        Path evalDir = "synctrip".equals(base.getFileName().toString())
                ? base.getParent().resolve("evaluation")
                : base.resolve("evaluation");
        Files.createDirectories(evalDir);
        Path out = evalDir.resolve("synctrip_pipeline.json");
        Files.writeString(out, json);

        // HTML이 file:// 로도 로드할 수 있도록 JS 래퍼도 함께 출력 (fetch CORS 회피)
        Path jsOut = evalDir.resolve("synctrip_pipeline.js");
        Files.writeString(jsOut, "window.PIPELINE_DATA = " + json + ";\n");

        System.out.println("✓ 파이프라인 JSON 추출 완료 → " + out.toAbsolutePath());
        System.out.printf("  방안 A 군집: K-Means=%.2fkm  Round-Robin=%.2fkm  단축=%.1f%%%n",
                kmeansTotal, rrTotal, rrTotal > 0 ? (1 - kmeansTotal / rrTotal) * 100 : 0);
        System.out.printf("  방안 A 라우팅: NN=%.2fkm  최적=%.2fkm  격차=%.1f%%%n",
                nnTotal, nnOptTotal, nnOptTotal > 0 ? (nnTotal / nnOptTotal - 1) * 100 : 0);
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    /** 후보 장소 공통 필드 (id/name/category/lat/lng) */
    private static Map<String, Object> placeBase(PlaceInfo p) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", p.placeId());
        o.put("name", p.name());
        o.put("category", p.category().name());
        o.put("lat", p.latitude());
        o.put("lng", p.longitude());
        return o;
    }

    /** [lat,lng,...] 배열 리스트에서 [lat,lng]만 추출한 복사본 반환 */
    private static List<double[]> stripPrio(List<double[]> pts) {
        List<double[]> out = new ArrayList<>(pts.size());
        for (double[] p : pts) out.add(new double[]{p[0], p[1]});
        return out;
    }

    /** Nearest Neighbor 오픈패스 거리(km) — 첫 장소에서 시작 (SimpleTsp 방식) */
    private static double nnRouteKm(List<double[]> pts) {
        if (pts.size() <= 1) return 0.0;
        List<double[]> remaining = new ArrayList<>(pts);
        double[] current = remaining.remove(0);
        double sum = 0;
        while (!remaining.isEmpty()) {
            int best = -1;
            double bd = Double.MAX_VALUE;
            for (int i = 0; i < remaining.size(); i++) {
                double d = haversine(current[0], current[1], remaining.get(i)[0], remaining.get(i)[1]);
                if (d < bd) { bd = d; best = i; }
            }
            sum += bd;
            current = remaining.remove(best);
        }
        return sum;
    }

    /** key/value 쌍으로 LinkedHashMap 생성 (삽입 순서 보존) */
    private static Map<String, Object> orderedMap(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    /** 완전탐색 최단 오픈패스 거리(km). remaining 순열 전체를 재귀 탐색한다. */
    private static double minPathKm(List<double[]> remaining, double[] last, double accum) {
        if (remaining.isEmpty()) return accum;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < remaining.size(); i++) {
            double[] p = remaining.get(i);
            double add = last == null ? 0.0 : haversine(last[0], last[1], p[0], p[1]);
            List<double[]> next = new ArrayList<>(remaining);
            next.remove(i);
            best = Math.min(best, minPathKm(next, p, accum + add));
        }
        return best;
    }

    private static double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return AlgorithmConstants.EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static double round(double v, int digits) {
        double f = Math.pow(10, digits);
        return Math.round(v * f) / f;
    }

    // ── 도쿄 시나리오 데이터 (TokyoTripScenarioTest와 동일) ────────────────────

    private static final long P_SENSOJI = 1L, P_SKYTREE = 2L, P_SHIBUYA = 3L, P_TEAMLAB = 4L,
            P_TSUKIJI = 5L, P_SHINJUKU_PARK = 6L, P_MEIJI = 7L, P_AKIHABARA = 8L, P_HARAJUKU = 9L,
            P_UENO = 10L, P_MUSEUM = 11L, P_RAMEN = 13L, P_SUSHI = 14L, P_TOWER = 16L,
            P_ODAIBA = 17L, P_NIKKO = 19L, P_HAMARIKYU = 20L,
            P_ROPPONGI = 12L, P_IZAKAYA = 15L, P_GINZA_SHP = 18L;

    private static List<PlaceInfo> buildPlaces() {
        return List.of(
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
            new PlaceInfo(P_NIKKO,         1L, "닛코 도쇼구",       PlaceCategory.CULTURE,   3, 240, 36.7585, 139.5990),
            new PlaceInfo(P_HAMARIKYU,     1L, "하마리큐 정원",     PlaceCategory.NATURE,    1, 90,  35.6600, 139.7648),
            new PlaceInfo(P_ROPPONGI,  1L, "롯폰기 힐즈",    PlaceCategory.ACTIVITY,  2, 90,  35.6604, 139.7292),
            new PlaceInfo(P_IZAKAYA,   1L, "이자카야 신주쿠", PlaceCategory.FOOD,      1, 90,  35.6896, 139.6957),
            new PlaceInfo(P_GINZA_SHP, 1L, "긴자 쇼핑거리",  PlaceCategory.SHOPPING,  2, 90,  35.6719, 139.7673)
        );
    }

    private static List<VoteInfo> buildVotes() {
        List<VoteInfo> v = new ArrayList<>();
        for (long uid = 1; uid <= 4; uid++) {
            v.add(new VoteInfo(P_SENSOJI, uid, 1));
            v.add(new VoteInfo(P_SKYTREE, uid, 1));
            v.add(new VoteInfo(P_SHIBUYA, uid, 1));
            v.add(new VoteInfo(P_TSUKIJI, uid, 1));
            v.add(new VoteInfo(P_RAMEN,   uid, 1));
        }
        for (long uid = 1; uid <= 3; uid++) {
            v.add(new VoteInfo(P_SHINJUKU_PARK, uid, 1));
            v.add(new VoteInfo(P_MEIJI,         uid, 1));
            v.add(new VoteInfo(P_AKIHABARA,     uid, 1));
            v.add(new VoteInfo(P_UENO,          uid, 1));
            v.add(new VoteInfo(P_SUSHI,         uid, 1));
            v.add(new VoteInfo(P_HAMARIKYU,     uid, 1));
        }
        v.add(new VoteInfo(P_TEAMLAB, 1L, 1));
        v.add(new VoteInfo(P_TEAMLAB, 2L, 1));
        v.add(new VoteInfo(P_TEAMLAB, 3L, 1));
        v.add(new VoteInfo(P_TEAMLAB, 4L, -1));
        v.add(new VoteInfo(P_HARAJUKU, 1L, 1));
        v.add(new VoteInfo(P_HARAJUKU, 2L, 1));
        v.add(new VoteInfo(P_MUSEUM,   1L, 1));
        v.add(new VoteInfo(P_MUSEUM,   2L, 1));
        v.add(new VoteInfo(P_TOWER,    1L, 1));
        v.add(new VoteInfo(P_TOWER,    2L, 1));
        v.add(new VoteInfo(P_ODAIBA,   1L, 1));
        v.add(new VoteInfo(P_ODAIBA,   2L, 1));
        v.add(new VoteInfo(P_NIKKO,    1L, 1));
        v.add(new VoteInfo(P_NIKKO,    2L, 1));
        v.add(new VoteInfo(P_ROPPONGI,  1L, 1));
        v.add(new VoteInfo(P_ROPPONGI,  2L, -1));
        v.add(new VoteInfo(P_GINZA_SHP, 1L, 1));
        v.add(new VoteInfo(P_GINZA_SHP, 2L, -1));
        v.add(new VoteInfo(P_IZAKAYA, 1L, 1));
        return v;
    }

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

    private static AlgorithmInput buildTokyoInput(List<PlaceInfo> places) {
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
            true,
            null, null
        );
        return new AlgorithmInput(
            group, members, places, buildVotes(),
            LocalTime.of(9, 0),
            buildOpeningHours()
        );
    }
}
