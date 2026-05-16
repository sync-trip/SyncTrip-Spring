package com.sync.algorithm.step3;

import com.sync.algorithm.AlgorithmConstants;
import com.sync.algorithm.step2.AssignedPlace;
import com.sync.algorithm.step2.DayGroup;
import com.sync.algorithm.step2.Step2Result;

import java.time.LocalTime;
import java.util.*;

public final class SimpleTsp {

    public static final LocalTime DEFAULT_DAY_START = LocalTime.of(9, 0);

    private SimpleTsp() {}

    /**
     * 각 일차의 장소를 Nearest Neighbor TSP로 정렬하고 시간을 할당한다.
     *
     * 설계 제약:
     * - 순수 함수 — DB 접근 없음, 입력 → 출력만
     * - 결정론적 — 같은 입력이면 항상 같은 출력
     * - 영업시간 체크는 해외(isOverseas=true)만 — 국내는 opening_hours=NULL
     */
    public static Step3Result schedule(Step3Input input) {
        Step2Result step2 = input.step2Result();
        LocalTime dayStart = input.dayStartTime() != null ? input.dayStartTime() : DEFAULT_DAY_START;

        List<DaySchedule> schedules = new ArrayList<>(step2.dayGroups().size());
        for (DayGroup dg : step2.dayGroups()) {
            schedules.add(scheduleDay(dg, dayStart, input.isOverseas(), input.openingHoursById()));
        }

        return new Step3Result(
                Collections.unmodifiableList(schedules),
                step2.overflow()
        );
    }

    private static DaySchedule scheduleDay(DayGroup dayGroup, LocalTime dayStart,
                                            boolean isOverseas,
                                            Map<Long, OpeningHours> openingHoursById) {
        List<AssignedPlace> places = dayGroup.places();
        if (places.isEmpty()) {
            return new DaySchedule(dayGroup.day(), List.of());
        }

        List<AssignedPlace> ordered = nearestNeighborTsp(places);
        List<ScheduledPlace> scheduled = assignTimes(
                ordered, dayGroup.day(), dayStart, isOverseas, openingHoursById);

        return new DaySchedule(dayGroup.day(), scheduled);
    }

    /**
     * Nearest Neighbor TSP.
     *
     * 시작점: places.get(0) — Step2가 우선순위 내림차순으로 정렬한 첫 번째 장소.
     * 동거리 동점: 입력 순서 앞쪽 우선 (strict < 비교로 결정론성 보장).
     */
    private static List<AssignedPlace> nearestNeighborTsp(List<AssignedPlace> places) {
        List<AssignedPlace> remaining = new ArrayList<>(places);
        List<AssignedPlace> route = new ArrayList<>(places.size());

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
                    next = candidate;
                }
            }
            remaining.remove(next);
            route.add(next);
            current = next;
        }

        return route;
    }

    /**
     * TSP 순서대로 시간을 순차 할당한다.
     * 이동 시간은 Haversine 거리 / TRAVEL_SPEED_KMH로 추정한다.
     * 해외이고 openingHoursById에 데이터가 있으면 슬롯이 영업시간을 벗어나는지 검사한다.
     */
    private static List<ScheduledPlace> assignTimes(List<AssignedPlace> ordered, int day,
                                                      LocalTime dayStart, boolean isOverseas,
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
            LocalTime end = current.plusMinutes(p.estimatedDuration());

            boolean violation = false;
            if (isOverseas) {
                OpeningHours oh = openingHoursById.get(p.placeId());
                if (oh != null) {
                    violation = start.isBefore(oh.open()) || end.isAfter(oh.close());
                }
            }

            result.add(new ScheduledPlace(
                    p.placeId(), day, i + 1,
                    p.category(), p.latitude(), p.longitude(),
                    p.estimatedDuration(), start, end,
                    p.priorityScore(), p.isOutlierCandidate(),
                    violation
            ));

            current = end;
        }

        return Collections.unmodifiableList(result);
    }

    /** TSP 이웃 선택용 — 같은 지역 내 상대 거리 비교이므로 Euclidean으로 충분 */
    private static double euclidean(double lat1, double lng1, double lat2, double lng2) {
        double dLat = lat1 - lat2;
        double dLng = lng1 - lng2;
        return Math.sqrt(dLat * dLat + dLng * dLng);
    }

    /** Haversine 거리(km) / 고정 속도 → 이동 시간(분, 반올림) */
    private static long travelMinutes(double lat1, double lng1, double lat2, double lng2) {
        double distKm = haversine(lat1, lng1, lat2, lng2);
        return Math.round(distKm / AlgorithmConstants.TRAVEL_SPEED_KMH * 60.0);
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
