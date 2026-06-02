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
    // 텍스트 검색 시 목적지 좌표 기준 결과 제한 반경 (미터, 최대 50,000)
    private static final double TEXT_SEARCH_RADIUS_METERS = 50_000;
    
    // API 응답에서 받아올 데이터 필드 정의 (필요한 필드만 선택하여 비용 최적화)
    private static final String FIELD_MASK =
            "places.id,places.displayName,places.location,places.types," +
            "places.regularOpeningHours,places.rating,places.formattedAddress,places.photos";

    // places.types 포함 — 도시/행정구역 타입 필터링에 사용 (기업·상점 등 제외)
    private static final String DESTINATION_FIELD_MASK =
            "places.id,places.displayName,places.location,places.formattedAddress," +
            "places.addressComponents,places.photos,places.types";
    
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
     * 키워드 기반 텍스트 검색.
     *
     * @param lat          목적지 위도
     * @param lng          목적지 경도
     * @param textQuery    검색 키워드
     * @param includedType 포함할 장소 타입 (예: "lodging"). null이면 전체 타입 검색.
     */
    public NearbySearchResponse searchText(double lat, double lng, String textQuery, String includedType) {
        // includedType 유무와 무관하게 항상 rectangle restriction으로 목적지 반경 밖 결과를 완전히 제외한다.
        // (locationBias는 "선호"일 뿐 제한이 아니어서, 동명의 타 도시 장소가 섞여 나오는 문제를 막을 수 없음)
        TextSearchRequest.LocationRestriction restriction =
                buildRectangleRestriction(lat, lng, TEXT_SEARCH_RADIUS_METERS);

        TextSearchRequest body = new TextSearchRequest(
                textQuery, null, restriction, MAX_RESULT_COUNT, "ko", includedType);

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
     * 도시/여행지 이름으로 전 세계 검색 (밴드 생성 목적지 선택 전용)
     * locationBias 없이 글로벌 검색, addressComponents로 국가 정보 포함
     */
    public NearbySearchResponse searchDestination(String textQuery) {
        TextSearchRequest body = new TextSearchRequest(
                textQuery,
                null,   // locationBias — 글로벌 검색이므로 위치 편향 없음
                null,   // locationRestriction — 반경 제한 없음
                5,
                "ko",
                null    // includedType — 도시/여행지 검색이므로 타입 제한 없음
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("X-Goog-Api-Key", properties.apiKey());
        headers.set("X-Goog-FieldMask", DESTINATION_FIELD_MASK);

        HttpEntity<TextSearchRequest> request = new HttpEntity<>(body, headers);
        String url = properties.placesBaseUrl() + TEXT_SEARCH_PATH;

        try {
            log.info("Google Destination Search: query={}", textQuery);
            ResponseEntity<NearbySearchResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, NearbySearchResponse.class);
            NearbySearchResponse result = response.getBody();
            if (result == null) {
                throw new ResponseStatusException(BAD_GATEWAY, "Google API 응답이 비어 있습니다.");
            }
            return result;
        } catch (HttpStatusCodeException ex) {
            log.error("Google Destination Search 오류. Status: {}, Body: {}",
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
     * 중심 좌표와 반경(미터)으로 rectangle restriction을 계산한다.
     * 위도 1도 ≈ 111km, 경도 1도 ≈ 111km * cos(위도) 공식 적용.
     */
    private TextSearchRequest.LocationRestriction buildRectangleRestriction(double lat, double lng, double radiusMeters) {
        double latOffset = radiusMeters / 111_000.0;
        double lngOffset = radiusMeters / (111_000.0 * Math.cos(Math.toRadians(lat)));
        return new TextSearchRequest.LocationRestriction(
                new TextSearchRequest.Rectangle(
                        new TextSearchRequest.LatLng(lat - latOffset, lng - lngOffset),
                        new TextSearchRequest.LatLng(lat + latOffset, lng + lngOffset)
                )
        );
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

