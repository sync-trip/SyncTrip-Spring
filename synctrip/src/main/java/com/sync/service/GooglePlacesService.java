package com.sync.service;

import com.sync.config.GoogleMapsProperties;
import com.sync.dto.google.NearbySearchRequest;
import com.sync.dto.google.NearbySearchResponse;
import com.sync.dto.google.TextSearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

/**
 * 구글 장소 검색 서비스 (해외 전용)
 * - Google Places API (New)를 사용하여 주변 장소를 검색한다.
 * - 장소 상세 정보 및 사진 URL 생성 기능을 제공한다.
 */
@Service
public class GooglePlacesService {

    private static final Logger log = LoggerFactory.getLogger(GooglePlacesService.class);

    private static final String NEARBY_SEARCH_PATH = "/v1/places:searchNearby";
    private static final String TEXT_SEARCH_PATH = "/v1/places:searchText";
    private static final double TEXT_SEARCH_BIAS_RADIUS_METERS = 50_000;
    
    // API 응답에서 받아올 데이터 필드 정의 (필요한 필드만 선택하여 비용 최적화)
    private static final String FIELD_MASK =
            "places.id,places.displayName,places.location,places.types," +
            "places.regularOpeningHours,places.rating,places.formattedAddress,places.photos";
    
    // API 1회 호출 시 최대 결과 개수 (구글 기본값 20)
    private static final int MAX_RESULT_COUNT = 20;

    private final RestTemplate restTemplate;
    private final GoogleMapsProperties properties;

    public GooglePlacesService(RestTemplate restTemplate, GoogleMapsProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    /**
     * 특정 좌표 중심의 주변 장소를 검색한다.
     *
     * @param lat           중심 위도
     * @param lng           중심 경도
     * @param radiusMeters  검색 반경 (미터 단위, 최대 50,000)
     * @param includedTypes 검색에 포함할 구글 장소 타입 리스트 (예: restaurant, museum)
     * @return 구글 API 응답 객체 (검색된 장소 목록 포함)
     */
    public NearbySearchResponse searchNearby(double lat, double lng,
                                              double radiusMeters,
                                              List<String> includedTypes) {
        // 구글 API 요청 바디 생성
        NearbySearchRequest body = new NearbySearchRequest(
                new NearbySearchRequest.LocationRestriction(
                        new NearbySearchRequest.Circle(
                                new NearbySearchRequest.LatLng(lat, lng),
                                radiusMeters)),
                includedTypes,
                MAX_RESULT_COUNT,
                "ko" // 검색 결과 언어 설정 (한국어)
        );

        HttpHeaders headers = buildHeaders();
        HttpEntity<NearbySearchRequest> request = new HttpEntity<>(body, headers);
        String url = properties.placesBaseUrl() + NEARBY_SEARCH_PATH;

        try {
            log.info("Google Places API 호출: lat={}, lng={}, radius={}m, types={}",
                    lat, lng, radiusMeters, includedTypes);
            ResponseEntity<NearbySearchResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, NearbySearchResponse.class);

            NearbySearchResponse result = response.getBody();
            if (result == null) {
                throw new ResponseStatusException(BAD_GATEWAY, "Google API 응답이 비어 있습니다.");
            }
            return result;
        } catch (HttpStatusCodeException ex) {
            log.error("Google API 호출 중 오류 발생. Status: {}, Body: {}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ResponseStatusException(BAD_GATEWAY,
                    "Google API 연동 실패: " + ex.getResponseBodyAsString(), ex);
        }
    }

    /**
     * 키워드 기반 텍스트 검색 (해외 장소 검색 전용)
     * locationBias로 목적지 주변 결과를 우선하되, 엄격히 제한하지는 않는다.
     *
     * @param lat           목적지 위도 (bias 중심)
     * @param lng           목적지 경도 (bias 중심)
     * @param textQuery     검색 키워드
     * @param includedTypes 카테고리 필터 (null 허용)
     */
    public NearbySearchResponse searchText(double lat, double lng,
                                            String textQuery,
                                            List<String> includedTypes) {
        TextSearchRequest body = new TextSearchRequest(
                textQuery,
                new TextSearchRequest.LocationBias(
                        new TextSearchRequest.Circle(
                                new TextSearchRequest.LatLng(lat, lng),
                                TEXT_SEARCH_BIAS_RADIUS_METERS)),
                (includedTypes == null || includedTypes.isEmpty()) ? null : includedTypes,
                MAX_RESULT_COUNT,
                "ko"
        );

        HttpHeaders headers = buildHeaders();
        HttpEntity<TextSearchRequest> request = new HttpEntity<>(body, headers);
        String url = properties.placesBaseUrl() + TEXT_SEARCH_PATH;

        try {
            log.info("Google Text Search API 호출: textQuery={}, lat={}, lng={}", textQuery, lat, lng);
            ResponseEntity<NearbySearchResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, NearbySearchResponse.class);

            NearbySearchResponse result = response.getBody();
            if (result == null) {
                throw new ResponseStatusException(BAD_GATEWAY, "Google API 응답이 비어 있습니다.");
            }
            return result;
        } catch (HttpStatusCodeException ex) {
            log.error("Google Text Search API 오류. Status: {}, Body: {}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ResponseStatusException(BAD_GATEWAY,
                    "Google API 연동 실패: " + ex.getResponseBodyAsString(), ex);
        }
    }

    /**
     * 구글 장소 사진 참조값을 실제 접근 가능한 이미지 URL로 변환한다.
     * @param photoName 구글에서 반환된 사진 리소스 이름
     * @return 400x400 크기의 이미지 접근 URL
     */
    public String buildPhotoUrl(String photoName) {
        return properties.placesBaseUrl() + "/v1/" + photoName
                + "/media?maxHeightPx=400&maxWidthPx=400&key=" + properties.apiKey();
    }

    /**
     * API 인증을 위한 HTTP 헤더 및 필드 마스크 설정
     */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("X-Goog-Api-Key", properties.apiKey()); // API 키 설정
        headers.set("X-Goog-FieldMask", FIELD_MASK); // 응답 필드 제한 (비용 절감)
        return headers;
    }
}

