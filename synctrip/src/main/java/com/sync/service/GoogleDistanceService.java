package com.sync.service;

import com.sync.algorithm.AlgorithmConstants;
import com.sync.config.GoogleMapsProperties;
import com.sync.dto.google.DirectionsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Google Directions API(transit 모드)로 두 좌표 간 실제 이동 시간과 노선 정보를 조회한다.
 * API 실패 시 하버사인 공식으로 fallback.
 */
@Service
public class GoogleDistanceService {

    private static final Logger log = LoggerFactory.getLogger(GoogleDistanceService.class);
    private static final String DIRECTIONS_URL =
            "https://maps.googleapis.com/maps/api/directions/json";

    private final RestTemplate restTemplate;
    private final GoogleMapsProperties properties;

    public GoogleDistanceService(RestTemplate restTemplate, GoogleMapsProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    /**
     * 두 좌표 간 대중교통 이동 정보(시간 + 노선)를 반환한다.
     * Google Directions API(transit) 호출 → 실패 시 하버사인 fallback.
     *
     * @param departureTimeUnix 출발 시각 (Unix timestamp, UTC). transit 모드에 필수.
     */
    public TravelInfo getTravelInfo(double fromLat, double fromLng,
                                    double toLat, double toLng,
                                    long departureTimeUnix) {
        // departure_time을 사람이 읽기 쉬운 UTC 문자열로 변환해 로그에 표시
        String deptReadable = Instant.ofEpochSecond(departureTimeUnix)
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        log.info("[Directions] 호출: ({},{})→({},{}) dept={} (unix={})",
                fromLat, fromLng, toLat, toLng, deptReadable, departureTimeUnix);

        try {
            String url = DIRECTIONS_URL
                    + "?origin=" + fromLat + "," + fromLng
                    + "&destination=" + toLat + "," + toLng
                    + "&mode=transit"
                    + "&departure_time=" + departureTimeUnix
                    + "&language=ko"
                    + "&key=" + properties.apiKey();

            DirectionsResponse response = restTemplate.getForObject(url, DirectionsResponse.class);

            if (response == null) {
                log.warn("[Directions] 응답 null, fallback");
                return TravelInfo.fallback(haversineFallback(fromLat, fromLng, toLat, toLng));
            }

            log.info("[Directions] 응답 status={} routes={}",
                    response.status(),
                    response.routes() == null ? "null" : response.routes().size());

            if ("OK".equals(response.status())
                    && response.routes() != null
                    && !response.routes().isEmpty()
                    && !response.routes().get(0).legs().isEmpty()) {

                DirectionsResponse.Leg leg = response.routes().get(0).legs().get(0);
                int minutes = (int) Math.ceil(leg.duration().value() / 60.0);
                minutes = Math.max(AlgorithmConstants.MIN_TRAVEL_MINUTES, minutes);

                // 각 step의 travel_mode 로그 — TRANSIT 스텝이 없으면 summary가 null임을 추적
                if (leg.steps() != null) {
                    List<String> modes = leg.steps().stream()
                            .map(s -> s.travelMode() != null ? s.travelMode() : "?")
                            .toList();
                    log.info("[Directions] {}분, steps modes={}", minutes, modes);
                }

                String summary = buildTransitSummary(leg.steps());
                log.info("[Directions] transitSummary={}", summary);
                return new TravelInfo(minutes, summary);
            }

            // OK가 아닌 status — 원인 파악용 상세 로그
            log.warn("[Directions] 비정상 status={}, 좌표: ({},{})→({},{}), dept={}",
                    response.status(), fromLat, fromLng, toLat, toLng, deptReadable);
        } catch (Exception e) {
            log.warn("[Directions] 호출 예외: {} — ({},{})→({},{})",
                    e.getMessage(), fromLat, fromLng, toLat, toLng);
        }
        return TravelInfo.fallback(haversineFallback(fromLat, fromLng, toLat, toLng));
    }

    /**
     * 경로 스텝에서 대중교통 노선 요약 문자열을 만든다.
     * TRANSIT 스텝이 없으면(도보만) null 반환.
     * 예: "丸ノ内線 → 日比谷線", "2호선", null
     */
    private String buildTransitSummary(List<DirectionsResponse.Step> steps) {
        if (steps == null || steps.isEmpty()) return null;

        List<String> lines = new ArrayList<>();
        for (DirectionsResponse.Step step : steps) {
            if (!"TRANSIT".equals(step.travelMode())) continue;
            if (step.transitDetails() == null || step.transitDetails().line() == null) continue;

            DirectionsResponse.Line line = step.transitDetails().line();
            // short_name 우선(예: "丸ノ内線"), 없으면 full name
            String name = line.shortName() != null ? line.shortName() : line.name();
            if (name != null && !lines.contains(name)) {
                lines.add(name);
            }
        }

        if (lines.isEmpty()) return null;
        return String.join(" → ", lines);
    }

    /** 하버사인 직선거리 기반 이동 시간 추정 (fallback 전용) */
    private int haversineFallback(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double distKm = AlgorithmConstants.EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        long estimated = Math.round(distKm / AlgorithmConstants.TRAVEL_SPEED_KMH * 60.0);
        return (int) Math.max(AlgorithmConstants.MIN_TRAVEL_MINUTES, estimated);
    }
}
