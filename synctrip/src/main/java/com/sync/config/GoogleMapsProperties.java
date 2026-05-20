package com.sync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google Maps API 설정을 환경변수에서 주입받는 설정 클래스
 *
 * application.yml에서 google.maps.* 로 시작하는 모든 설정을 자동으로 바인딩
 * 실제 값은 .env 파일에서 로드되고 Spring Boot가 자동 주입
 *
 * 사용 위치:
 * - GooglePlacesService: 해외 장소 검색 (Nearby Search API)
 *
 * 주의:
 * - apiKey는 Google Maps Platform에서 발급받은 유효한 API 키
 * - Places API 권한이 활성화되어 있어야 함
 * - placesBaseUrl은 고정 URL (Google 엔드포인트)
 */
@ConfigurationProperties(prefix = "google.maps")
public record GoogleMapsProperties(
        // Google Maps Platform API Key (필수, .env에서 주입)
        // Places API, Routes API 등 여러 서비스에 사용됨
        String apiKey,
        // Google Places API 베이스 URL (고정값)
        // https://places.googleapis.com으로 고정됨
        String placesBaseUrl
) {
}
