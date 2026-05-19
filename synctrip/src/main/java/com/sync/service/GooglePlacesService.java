package com.sync.service;

import com.sync.config.GoogleMapsProperties;
import com.sync.dto.google.NearbySearchRequest;
import com.sync.dto.google.NearbySearchResponse;
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

@Service
public class GooglePlacesService {

    private static final Logger log = LoggerFactory.getLogger(GooglePlacesService.class);

    private static final String NEARBY_SEARCH_PATH = "/v1/places:searchNearby";
    private static final String FIELD_MASK =
            "places.id,places.displayName,places.location,places.types," +
            "places.regularOpeningHours,places.rating,places.formattedAddress,places.photos";
    private static final int MAX_RESULT_COUNT = 20;

    private final RestTemplate restTemplate;
    private final GoogleMapsProperties properties;

    public GooglePlacesService(RestTemplate restTemplate, GoogleMapsProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    /**
     * Google Places API (New) — Nearby Search로 주변 장소를 검색한다.
     *
     * @param lat           중심 위도
     * @param lng           중심 경도
     * @param radiusMeters  검색 반경 (미터, 최대 50000)
     * @param includedTypes Google Places 유형 필터 (예: "restaurant", "tourist_attraction")
     */
    public NearbySearchResponse searchNearby(double lat, double lng,
                                              double radiusMeters,
                                              List<String> includedTypes) {
        NearbySearchRequest body = new NearbySearchRequest(
                new NearbySearchRequest.LocationRestriction(
                        new NearbySearchRequest.Circle(
                                new NearbySearchRequest.LatLng(lat, lng),
                                radiusMeters)),
                includedTypes,
                MAX_RESULT_COUNT,
                "ko"
        );

        HttpHeaders headers = buildHeaders();
        HttpEntity<NearbySearchRequest> request = new HttpEntity<>(body, headers);
        String url = properties.placesBaseUrl() + NEARBY_SEARCH_PATH;

        try {
            log.info("Google Places Nearby Search: lat={}, lng={}, radius={}m, types={}",
                    lat, lng, radiusMeters, includedTypes);
            ResponseEntity<NearbySearchResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, NearbySearchResponse.class);

            NearbySearchResponse result = response.getBody();
            if (result == null) {
                throw new ResponseStatusException(BAD_GATEWAY, "Google Places API 응답이 비어 있습니다.");
            }
            return result;
        } catch (HttpStatusCodeException ex) {
            log.error("Google Places API 오류. Status: {}, Body: {}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ResponseStatusException(BAD_GATEWAY,
                    "Google Places API 호출 실패: " + ex.getResponseBodyAsString(), ex);
        }
    }

    /**
     * Google Places 사진 이름으로 썸네일 URL을 생성한다.
     * Android Glide/Coil이 302 리다이렉트를 자동으로 따라간다.
     */
    public String buildPhotoUrl(String photoName) {
        return properties.placesBaseUrl() + "/v1/" + photoName
                + "/media?maxHeightPx=400&maxWidthPx=400&key=" + properties.apiKey();
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("X-Goog-Api-Key", properties.apiKey());
        headers.set("X-Goog-FieldMask", FIELD_MASK);
        return headers;
    }
}
