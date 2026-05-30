package com.sync.algorithm.step3;

import com.sync.algorithm.AlgorithmConstants;
import com.sync.algorithm.PlaceCategory;
import com.sync.algorithm.TravelStyle;
import com.sync.algorithm.step2.AssignedPlace;
import com.sync.algorithm.step2.DayGroup;
import com.sync.algorithm.step2.Step2Result;

import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

public final class SimpleTsp {

    public static final LocalTime DEFAULT_DAY_START = LocalTime.of(9, 0);

    // 식사 윈도우 경계 (PACKED: 점심+저녁 / RELAXED: 저녁만)
    private static final LocalTime LUNCH_WINDOW_START  = LocalTime.of(11, 0);
    private static final LocalTime LUNCH_WINDOW_END    = LocalTime.of(14, 0);
    private static final LocalTime DINNER_WINDOW_START = LocalTime.of(17, 0);
    private static final LocalTime DINNER_WINDOW_END   = LocalTime.of(20, 0);

    private SimpleTsp() {}

    /**
     * 각 일차의 장소를 Nearest Neighbor TSP로 정렬하고 시간을 할당한다.
     *
     * 설계 제약:
     * - 순수 함수 — DB 접근 없음, 입력 → 출력만
     * - 결정론적 — 같은 입력이면 항상 같은 출력
     * - 영업시간 체크는 해외(isOverseas=true)만 — 국내는 opening_hours=NULL
     * - FOOD는 식사 윈도우에 끼워넣기 (FIX-47) [작업1]
     */
    public static Step3Result schedule(Step3Input input) {
        Step2Result step2 = input.step2Result();
        LocalTime dayStart = input.dayStartTime() != null ? input.dayStartTime() : DEFAULT_DAY_START;
        TravelStyle style  = input.travelStyle()  != null ? input.travelStyle()  : TravelStyle.RELAXED;

        List<DaySchedule> schedules = new ArrayList<>(step2.dayGroups().size());
        for (DayGroup dg : step2.dayGroups()) {
            schedules.add(scheduleDay(dg, dayStart, style, input.isOverseas(), input.openingHoursById()));
        }

        return new Step3Result(
                Collections.unmodifiableList(schedules),
                step2.overflow()
        );
    }

    private static DaySchedule scheduleDay(DayGroup dayGroup, LocalTime dayStart,
                                            TravelStyle style, boolean isOverseas,
                                            Map<Long, OpeningHours> openingHoursById) {
        List<AssignedPlace> places = dayGroup.places();
        if (places.isEmpty()) {
            return new DaySchedule(dayGroup.day(), List.of(), false);
        }

        // FOOD와 비FOOD를 분리한다 (FIX-47)
        List<AssignedPlace> nonFood = places.stream()
                .filter(p -> p.category() != PlaceCategory.FOOD)
                .collect(Collectors.toList());
        List<AssignedPlace> foods = places.stream()
                .filter(p -> p.category() == PlaceCategory.FOOD)
                .collect(Collectors.toList());

        // 비FOOD만 Nearest Neighbor TSP로 동선 확정
        List<AssignedPlace> nonFoodOrdered = nonFood.isEmpty()
                ? Collections.emptyList()
                : nearestNeighborTsp(nonFood);

        // FOOD를 식사 윈도우에 삽입해 최종 순서 결정
        List<AssignedPlace> ordered = insertFoodByWindow(nonFoodOrdered, foods, style, dayStart);

        List<ScheduledPlace> scheduled = assignTimes(
                ordered, dayGroup.day(), dayStart, style, isOverseas, openingHoursById);

        // DAY_OVERLOADED: 마지막 슬롯 종료 시각이 22:00 초과 [작업3]
        boolean dayOverloaded = !scheduled.isEmpty()
                && scheduled.get(scheduled.size() - 1).endTime()
                        .isAfter(AlgorithmConstants.LATE_WARN_TIME);

        return new DaySchedule(dayGroup.day(), scheduled, dayOverloaded);
    }

    // ── FIX-47: FOOD 시간 윈도우 끼워넣기 ────────────────────────────────
    //
    // 비FOOD NN 순서를 고정한 뒤 FOOD를 식사 윈도우에 삽입한다.
    //   PACKED : 점심(11:00) + 저녁(17:00) 윈도우
    //   RELAXED: 저녁(17:00) 윈도우만
    //
    // 각 윈도우마다:
    //   1) 현재 combined 기준 도착 시각을 시뮬레이션한다.
    //   2) 윈도우 시작 시각 이후 첫 번째로 도달하는 슬롯 직전에 FOOD를 삽입한다.
    //   3) 삽입 기준점(직전 장소)에서 가장 가까운 FOOD를 선택한다 (nearest 방식).
    //
    // 모든 윈도우 소진 후 남은 FOOD는 일정 맨 뒤에 지리적 최근접 순으로 추가한다.

    private static List<AssignedPlace> insertFoodByWindow(
            List<AssignedPlace> nonFoodOrdered,
            List<AssignedPlace> foods,
            TravelStyle style,
            LocalTime dayStart) {

        if (foods.isEmpty()) {
            return new ArrayList<>(nonFoodOrdered);
        }

        List<LocalTime> windowStarts = new ArrayList<>();
        if (style == TravelStyle.PACKED) {
            windowStarts.add(LUNCH_WINDOW_START);
        }
        windowStarts.add(DINNER_WINDOW_START);

        List<AssignedPlace> combined  = new ArrayList<>(nonFoodOrdered);
        List<AssignedPlace> remaining = new ArrayList<>(foods);

        for (LocalTime windowStart : windowStarts) {
            if (remaining.isEmpty()) break;

            // 현재 combined 기준 도착 시각 시뮬레이션
            List<LocalTime> arrivals = simulateArrivals(combined, dayStart);

            // 윈도우 시작 이후 처음 도달하는 슬롯의 인덱스 (없으면 맨 끝)
            int insertIdx = combined.size();
            for (int i = 0; i < arrivals.size(); i++) {
                if (!arrivals.get(i).isBefore(windowStart)) {
                    insertIdx = i;
                    break;
                }
            }

            // 삽입 직전 장소(null이면 우선순위 첫 번째)를 기준으로 최근접 FOOD 선택
            AssignedPlace ref = insertIdx > 0 ? combined.get(insertIdx - 1) : null;
            AssignedPlace nearest = findNearest(remaining, ref);
            combined.add(insertIdx, nearest);
            remaining.remove(nearest);
        }

        // 남은 FOOD는 일정 끝에 지리적 최근접 순으로 추가
        while (!remaining.isEmpty()) {
            AssignedPlace ref = combined.isEmpty() ? null : combined.get(combined.size() - 1);
            AssignedPlace nearest = findNearest(remaining, ref);
            combined.add(nearest);
            remaining.remove(nearest);
        }

        return combined;
    }

    /** combined 기준 각 슬롯의 도착 시각을 시뮬레이션한다 (실제 할당 없이 순서만). */
    private static List<LocalTime> simulateArrivals(List<AssignedPlace> places, LocalTime dayStart) {
        List<LocalTime> arrivals = new ArrayList<>(places.size());
        LocalTime current = dayStart;
        for (int i = 0; i < places.size(); i++) {
            if (i > 0) {
                AssignedPlace prev = places.get(i - 1);
                AssignedPlace curr = places.get(i);
                long travelMin = travelMinutes(prev.latitude(), prev.longitude(),
                        curr.latitude(), curr.longitude());
                current = current.plusMinutes(travelMin);
            }
            arrivals.add(current);
            current = current.plusMinutes(places.get(i).estimatedDuration());
        }
        return arrivals;
    }

    /** candidates 중 ref에서 Haversine 최근접을 반환한다. ref가 null이면 candidates.get(0). */
    private static AssignedPlace findNearest(List<AssignedPlace> candidates, AssignedPlace ref) {
        if (ref == null || candidates.size() == 1) return candidates.get(0);
        AssignedPlace best   = candidates.get(0);
        double        minDist = haversine(ref.latitude(), ref.longitude(),
                best.latitude(), best.longitude());
        for (int i = 1; i < candidates.size(); i++) {
            AssignedPlace c = candidates.get(i);
            double d = haversine(ref.latitude(), ref.longitude(), c.latitude(), c.longitude());
            if (d < minDist) {
                minDist = d;
                best    = c;
            }
        }
        return best;
    }

    // ── Nearest Neighbor TSP ─────────────────────────────────────────────
    //
    // 비FOOD 장소만 받는다 (FIX-47: FOOD는 insertFoodByWindow에서 처리).
    // 시작점: places.get(0) — Step2가 우선순위 내림차순으로 정렬한 첫 번째 장소.
    // 동거리 동점: 입력 순서 앞쪽 우선 (strict < 비교로 결정론성 보장).

    private static List<AssignedPlace> nearestNeighborTsp(List<AssignedPlace> places) {
        List<AssignedPlace> remaining = new ArrayList<>(places);
        List<AssignedPlace> route     = new ArrayList<>(places.size());

        AssignedPlace current = remaining.remove(0);
        route.add(current);

        while (!remaining.isEmpty()) {
            double minDist = Double.MAX_VALUE;
            AssignedPlace next = null;
            for (AssignedPlace candidate : remaining) {
                double d = euclidean(current.latitude(), current.longitude(),
                        candidate.latitude(), candidate.longitude());
                if (d < minDist) {
                    minDist = d;
                    next    = candidate;
                }
            }
            remaining.remove(next);
            route.add(next);
            current = next;
        }

        return route;
    }

    // ── 시간 할당 + 경고 배지 3종 ────────────────────────────────────────
    //
    // - openingHoursViolation  : 해외이고 영업시간 범위 외
    // - mealWindowViolation    : FOOD 슬롯의 startTime이 식사 윈도우 밖 [작업2]
    // - lateSchedule           : startTime >= 22:00 [작업2]
    // - openingHoursUnverified : isOverseas=true이고 영업시간 데이터 없음 [작업2]

    private static List<ScheduledPlace> assignTimes(List<AssignedPlace> ordered, int day,
                                                      LocalTime dayStart, TravelStyle style,
                                                      boolean isOverseas,
                                                      Map<Long, OpeningHours> openingHoursById) {
        List<ScheduledPlace> result = new ArrayList<>(ordered.size());
        LocalTime current = dayStart;

        for (int i = 0; i < ordered.size(); i++) {
            AssignedPlace p = ordered.get(i);

            if (i > 0) {
                AssignedPlace prev = ordered.get(i - 1);
                long travelMin = travelMinutes(prev.latitude(), prev.longitude(),
                        p.latitude(), p.longitude());
                current = current.plusMinutes(travelMin);
            }

            LocalTime start = current;
            LocalTime end   = current.plusMinutes(p.estimatedDuration());

            // 영업시간 위반 (해외 전용)
            boolean violation = false;
            if (isOverseas) {
                OpeningHours oh = openingHoursById.get(p.placeId());
                if (oh != null) {
                    violation = start.isBefore(oh.open()) || end.isAfter(oh.close());
                }
            }

            // 식사 윈도우 외 배치 경고 (FOOD 슬롯만)
            boolean mealWindowViolation = p.category() == PlaceCategory.FOOD
                    && !isInAnyMealWindow(start, style);

            // 22:00 이후 시작 경고
            boolean lateSchedule = !start.isBefore(AlgorithmConstants.LATE_WARN_TIME);

            // 해외이고 영업시간 데이터 없음
            boolean openingHoursUnverified = isOverseas
                    && openingHoursById.get(p.placeId()) == null;

            result.add(new ScheduledPlace(
                    p.placeId(), day, i + 1,
                    p.category(), p.latitude(), p.longitude(),
                    p.estimatedDuration(), start, end,
                    p.priorityScore(), p.isOutlierCandidate(),
                    violation, mealWindowViolation, lateSchedule, openingHoursUnverified
            ));

            current = end;
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * FOOD 슬롯의 startTime이 해당 스타일의 식사 윈도우 중 하나 안에 있으면 true.
     * PACKED: 점심(11:00~14:00) 또는 저녁(17:00~20:00)
     * RELAXED: 저녁(17:00~20:00)만
     */
    private static boolean isInAnyMealWindow(LocalTime time, TravelStyle style) {
        boolean inDinner = !time.isBefore(DINNER_WINDOW_START) && time.isBefore(DINNER_WINDOW_END);
        if (style == TravelStyle.PACKED) {
            boolean inLunch = !time.isBefore(LUNCH_WINDOW_START) && time.isBefore(LUNCH_WINDOW_END);
            return inLunch || inDinner;
        }
        return inDinner;
    }

    /** TSP 이웃 선택용 — 같은 지역 내 상대 거리 비교이므로 Euclidean으로 충분 */
    private static double euclidean(double lat1, double lng1, double lat2, double lng2) {
        double dLat = lat1 - lat2;
        double dLng = lng1 - lng2;
        return Math.sqrt(dLat * dLat + dLng * dLng);
    }

    /**
     * Haversine 거리(km) / 고정 속도 → 이동 시간(분, 반올림).
     * MIN_TRAVEL_MINUTES 하한 적용 — 0km 구간도 최소 3분 [작업4]
     */
    private static long travelMinutes(double lat1, double lng1, double lat2, double lng2) {
        double distKm   = haversine(lat1, lng1, lat2, lng2);
        long   estimated = Math.round(distKm / AlgorithmConstants.TRAVEL_SPEED_KMH * 60.0);
        return Math.max(AlgorithmConstants.MIN_TRAVEL_MINUTES, estimated);
    }

    private static double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return AlgorithmConstants.EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
