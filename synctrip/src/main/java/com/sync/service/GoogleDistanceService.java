package com.sync.service;

import com.sync.algorithm.AlgorithmConstants;
import com.sync.config.GoogleMapsProperties;
import com.sync.dto.google.RoutesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Google Routes API(신형, travelMode=TRANSIT)로 두 좌표 간 실제 이동 시간과 노선 정보를 조회한다.
 * API 실패 시 하버사인 공식으로 fallback.
 *
 * 구형 Directions API(maps.googleapis.com/maps/api/directions)에서 마이그레이션됨.
 * Routes API는 POST + JSON 바디 + FieldMask 헤더 방식을 사용한다.
 */
@Service
public class GoogleDistanceService {

    private static final Logger log = LoggerFactory.getLogger(GoogleDistanceService.class);
    private static final String ROUTES_URL =
            "https://routes.googleapis.com/directions/v2:computeRoutes";

    // 응답에서 받을 필드만 명시 — Routes API는 FieldMask가 필수이며, 받는 만큼만 과금/직렬화된다.
    private static final String FIELD_MASK =
            "routes.duration,"
            + "routes.legs.steps.travelMode,"
            + "routes.legs.steps.transitDetails.transitLine.name,"
            + "routes.legs.steps.transitDetails.transitLine.nameShort";

    private final RestTemplate restTemplate;
    private final GoogleMapsProperties properties;

    public GoogleDistanceService(RestTemplate restTemplate, GoogleMapsProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    /**
     * 두 좌표 간 대중교통 이동 정보(시간 + 노선)를 반환한다.
     * Google Routes API(transit) 호출 → 실패 시 하버사인 fallback.
     *
     * @param departureTimeUnix 출발 시각 (Unix timestamp, UTC). transit 모드에 필수.
     *                          내부에서 RFC3339 문자열로 변환해 요청한다.
     */
    public TravelInfo getTravelInfo(double fromLat, double fromLng,
                                    double toLat, double toLng,
                                    long departureTimeUnix) {
        // Routes API는 departureTime을 RFC3339(UTC "Z") 문자열로 요구한다.
        String departureTime = Instant.ofEpochSecond(departureTimeUnix)
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT);
        log.info("[Routes] 호출: ({},{})→({},{}) dept={} (unix={})",
                fromLat, fromLng, toLat, toLng, departureTime, departureTimeUnix);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Goog-Api-Key", properties.apiKey());
            headers.set("X-Goog-FieldMask", FIELD_MASK);

            // 요청 바디 — 출발/도착 좌표, transit 모드, 출발 시각, 한국어 결과
            Map<String, Object> body = Map.of(
                    "origin", latLngWaypoint(fromLat, fromLng),
                    "destination", latLngWaypoint(toLat, toLng),
                    "travelMode", "TRANSIT",
                    "departureTime", departureTime,
                    "languageCode", "ko"
            );

            RoutesResponse response = restTemplate.postForObject(
                    ROUTES_URL, new HttpEntity<>(body, headers), RoutesResponse.class);

            if (response == null || response.routes() == null || response.routes().isEmpty()) {
                log.warn("[Routes] 경로 없음(response={}), fallback",
                        response == null ? "null" : "routes=empty");
                return TravelInfo.fallback(haversineFallback(fromLat, fromLng, toLat, toLng));
            }

            RoutesResponse.Route route = response.routes().get(0);
            int minutes = parseDurationMinutes(route.duration());
            minutes = Math.max(AlgorithmConstants.MIN_TRAVEL_MINUTES, minutes);

            List<RoutesResponse.Step> steps =
                    (route.legs() == null || route.legs().isEmpty()) ? null : route.legs().get(0).steps();

            // 각 step의 travelMode 로그 — TRANSIT 스텝이 없으면 summary가 null임을 추적
            if (steps != null) {
                List<String> modes = steps.stream()
                        .map(s -> s.travelMode() != null ? s.travelMode() : "?")
                        .toList();
                log.info("[Routes] {}분, steps modes={}", minutes, modes);
            }

            String summary = buildTransitSummary(steps);
            log.info("[Routes] transitSummary={}", summary);
            return new TravelInfo(minutes, summary);
        } catch (Exception e) {
            // Routes API는 비정상 시 HTTP 4xx/5xx로 응답 → 예외 발생 → fallback
            log.warn("[Routes] 호출 예외: {} — ({},{})→({},{})",
                    e.getMessage(), fromLat, fromLng, toLat, toLng);
        }
        return TravelInfo.fallback(haversineFallback(fromLat, fromLng, toLat, toLng));
    }

    /** Routes API waypoint(좌표) 구조 생성: {location:{latLng:{latitude,longitude}}} */
    private Map<String, Object> latLngWaypoint(double lat, double lng) {
        return Map.of("location", Map.of("latLng", Map.of(
                "latitude", lat,
                "longitude", lng
        )));
    }

    /** Routes API duration 문자열("1860s")을 분 단위로 변환. 파싱 실패 시 최소값. */
    private int parseDurationMinutes(String duration) {
        if (duration == null || duration.isBlank()) {
            return AlgorithmConstants.MIN_TRAVEL_MINUTES;
        }
        try {
            // "1860s" → 끝의 's' 제거 후 초 단위 정수 파싱
            long seconds = Long.parseLong(duration.replace("s", "").trim());
            return (int) Math.ceil(seconds / 60.0);
        } catch (NumberFormatException e) {
            log.warn("[Routes] duration 파싱 실패: {}", duration);
            return AlgorithmConstants.MIN_TRAVEL_MINUTES;
        }
    }

    /**
     * 경로 스텝에서 대중교통 노선 요약 문자열을 만든다.
     * TRANSIT 스텝이 없으면(도보만) null 반환.
     * 예: "丸ノ内線 → 日比谷線", "2호선", null
     */
    private String buildTransitSummary(List<RoutesResponse.Step> steps) {
        if (steps == null || steps.isEmpty()) return null;

        List<String> lines = new ArrayList<>();
        for (RoutesResponse.Step step : steps) {
            if (!"TRANSIT".equals(step.travelMode())) continue;
            if (step.transitDetails() == null || step.transitDetails().transitLine() == null) continue;

            RoutesResponse.TransitLine line = step.transitDetails().transitLine();
            // nameShort 우선(예: "丸ノ内線"), 없으면 full name
            String name = line.nameShort() != null ? line.nameShort() : line.name();
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
