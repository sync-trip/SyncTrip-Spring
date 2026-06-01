package com.sync.service;

import com.sync.algorithm.AlgorithmConstants;
import com.sync.config.GoogleMapsProperties;
import com.sync.dto.google.DistanceMatrixResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Google Distance Matrix API를 사용해 두 좌표 간 실제 이동 시간(분)을 조회한다.
 * driving 모드 기준 (도로 기반, 교통 정보 반영 없음).
 * API 호출 실패 또는 경로 없음 시 하버사인 공식으로 fallback.
 */
@Service
public class GoogleDistanceService {

    private static final Logger log = LoggerFactory.getLogger(GoogleDistanceService.class);
    private static final String DISTANCE_MATRIX_URL =
            "https://maps.googleapis.com/maps/api/distancematrix/json";

    private final RestTemplate restTemplate;
    private final GoogleMapsProperties properties;

    public GoogleDistanceService(RestTemplate restTemplate, GoogleMapsProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    /**
     * 두 좌표 간 실제 이동 시간(분)을 반환한다.
     * Google Distance Matrix API(driving 모드) 호출 → 실패 시 하버사인 fallback.
     */
    public int getTravelMinutes(double fromLat, double fromLng, double toLat, double toLng) {
        try {
            String url = DISTANCE_MATRIX_URL
                    + "?origins=" + fromLat + "," + fromLng
                    + "&destinations=" + toLat + "," + toLng
                    + "&mode=driving"
                    + "&key=" + properties.apiKey();

            DistanceMatrixResponse response = restTemplate.getForObject(url, DistanceMatrixResponse.class);

            if (response != null
                    && "OK".equals(response.status())
                    && response.rows() != null
                    && !response.rows().isEmpty()
                    && response.rows().get(0).elements() != null
                    && !response.rows().get(0).elements().isEmpty()) {

                DistanceMatrixResponse.Element element = response.rows().get(0).elements().get(0);
                if ("OK".equals(element.status()) && element.duration() != null) {
                    // duration.value()는 초 단위 → 분으로 변환 후 MIN_TRAVEL_MINUTES 하한 적용
                    int minutes = (int) Math.ceil(element.duration().value() / 60.0);
                    return Math.max(AlgorithmConstants.MIN_TRAVEL_MINUTES, minutes);
                }
            }

            log.warn("Distance Matrix API 경로 없음, fallback: ({},{})→({},{})",
                    fromLat, fromLng, toLat, toLng);
        } catch (Exception e) {
            log.warn("Distance Matrix API 호출 실패, fallback: {}", e.getMessage());
        }
        return haversineFallback(fromLat, fromLng, toLat, toLng);
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
