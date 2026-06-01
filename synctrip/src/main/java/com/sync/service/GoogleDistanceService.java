package com.sync.service;

import com.sync.algorithm.AlgorithmConstants;
import com.sync.config.GoogleMapsProperties;
import com.sync.dto.google.DirectionsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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
        try {
            String url = DIRECTIONS_URL
                    + "?origin=" + fromLat + "," + fromLng
                    + "&destination=" + toLat + "," + toLng
                    + "&mode=transit"
                    + "&departure_time=" + departureTimeUnix
                    + "&language=ko"
                    + "&key=" + properties.apiKey();

            DirectionsResponse response = restTemplate.getForObject(url, DirectionsResponse.class);

            if (response != null
                    && "OK".equals(response.status())
                    && response.routes() != null
                    && !response.routes().isEmpty()
                    && !response.routes().get(0).legs().isEmpty()) {

                DirectionsResponse.Leg leg = response.routes().get(0).legs().get(0);
                int minutes = (int) Math.ceil(leg.duration().value() / 60.0);
                minutes = Math.max(AlgorithmConstants.MIN_TRAVEL_MINUTES, minutes);

                String summary = buildTransitSummary(leg.steps());
                return new TravelInfo(minutes, summary);
            }

            log.warn("Directions API 경로 없음, fallback: ({},{})→({},{})",
                    fromLat, fromLng, toLat, toLng);
        } catch (Exception e) {
            log.warn("Directions API 호출 실패, fallback: {}", e.getMessage());
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
