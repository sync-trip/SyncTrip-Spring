package com.sync.service;

import com.sync.config.KakaoProperties;
import com.sync.domain.place.PlaceCategory;
import com.sync.dto.kakao.KakaoLocalSearchResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

/**
 * 카카오 장소 검색 서비스 (국내 전용)
 * - Kakao Local API를 사용하여 주변 장소를 검색한다.
 * - 카테고리 필터링 및 검색 결과 중복 제거 기능을 제공한다.
 */
@Service
public class KakaoPlacesService {

    private static final Logger log = LoggerFactory.getLogger(KakaoPlacesService.class);
    // API 1회 호출 시 최대 결과 개수 (카카오 기본값 15)
    private static final int MAX_RESULT_COUNT = 15;

    /**
     * 서비스 내부 카테고리(PlaceCategory) -> Kakao API 그룹 코드 매핑
     * FD6: 음식점, CE7: 카페, CT1: 문화시설, AT4: 관광명소, SC4: 쇼핑, PK6: 공원
     */
    private static final Map<PlaceCategory, List<String>> CATEGORY_TO_GROUP_CODES = Map.of(
            PlaceCategory.FOOD, List.of("FD6", "CE7"),
            PlaceCategory.CULTURE, List.of("CT1", "AT4"),
            PlaceCategory.ACTIVITY, List.of("AT4"),
            PlaceCategory.SHOPPING, List.of("SC4"),
            PlaceCategory.NATURE, List.of("PK6"),
            PlaceCategory.ETC, List.of("FD6", "CE7", "CT1", "AT4", "SC4", "PK6")
    );

    private final RestTemplate restTemplate;
    private final KakaoProperties kakaoProperties;

    public KakaoPlacesService(RestTemplate restTemplate, KakaoProperties kakaoProperties) {
        this.restTemplate = restTemplate;
        this.kakaoProperties = kakaoProperties;
    }

    /**
     * 특정 좌표 중심의 주변 장소를 카테고리별로 검색한다.
     *
     * @param lat          중심 위도 (Y)
     * @param lng          중심 경도 (X)
     * @param radiusMeters 검색 반경 (미터 단위, 기본 5000)
     * @param category     서비스 내부 카테고리 필터 (null인 경우 전체)
     * @return 중복 제거된 장소 목록 (거리순 정렬 기반)
     */
    public List<KakaoLocalSearchResponse.Document> searchNearby(double lat, double lng,
                                                                double radiusMeters,
                                                                PlaceCategory category) {
        // 1. 요청된 카테고리에 해당하는 카카오 그룹 코드 리스트를 가져온다.
        List<String> groupCodes = resolveGroupCodes(category);
        if (groupCodes.isEmpty()) {
            return List.of();
        }

        // 반경 설정 (값이 없으면 기본 5km)
        double effectiveRadius = radiusMeters > 0 ? radiusMeters : 5000.0;
        
        // 동일 장소가 여러 카테고리에 걸쳐 검색될 수 있으므로 ID를 키로 하여 중복 제거
        Map<String, KakaoLocalSearchResponse.Document> uniqueById = new LinkedHashMap<>();

        // 2. 각 그룹 코드별로 API를 호출하여 결과를 병합한다.
        for (String groupCode : groupCodes) {
            KakaoLocalSearchResponse response = searchByCategoryGroupCode(
                    lat, lng, effectiveRadius, groupCode);
            if (response == null || response.documents() == null || response.documents().isEmpty()) {
                continue;
            }

            for (KakaoLocalSearchResponse.Document doc : response.documents()) {
                if (doc == null || doc.id() == null) continue;
                uniqueById.putIfAbsent(doc.id(), doc); // 먼저 발견된 장소(거리순) 유지
            }
        }

        return new ArrayList<>(uniqueById.values());
    }

    /**
     * 카카오 카테고리 그룹 검색 API를 호출한다.
     */
    private KakaoLocalSearchResponse searchByCategoryGroupCode(double lat, double lng,
                                                               double radiusMeters,
                                                               String categoryGroupCode) {
        // 카카오 API URL 구성
        // 주의: 카카오는 x가 경도(lng), y가 위도(lat)이다.
        String url = UriComponentsBuilder.fromUriString(kakaoProperties.localSearchUri())
                .queryParam("category_group_code", categoryGroupCode)
                .queryParam("x", lng)
                .queryParam("y", lat)
                .queryParam("radius", (int) Math.round(radiusMeters))
                .queryParam("sort", "distance") // 가까운 순으로 정렬
                .queryParam("size", MAX_RESULT_COUNT)
                .toUriString();

        HttpEntity<Void> request = new HttpEntity<>(buildHeaders());

        try {
            log.info("Kakao Places API 호출: lat={}, lng={}, radius={}m, groupCode={}",
                    lat, lng, radiusMeters, categoryGroupCode);
            ResponseEntity<KakaoLocalSearchResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, KakaoLocalSearchResponse.class);

            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            log.error("Kakao API 호출 중 오류 발생. Status: {}, Body: {}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ResponseStatusException(BAD_GATEWAY,
                    "Kakao API 연동 실패: " + ex.getResponseBodyAsString(), ex);
        }
    }

    /**
     * 서비스 카테고리를 카카오 API용 그룹 코드로 변환한다.
     */
    private List<String> resolveGroupCodes(PlaceCategory category) {
        if (category == null) {
            return CATEGORY_TO_GROUP_CODES.get(PlaceCategory.ETC);
        }
        return CATEGORY_TO_GROUP_CODES.getOrDefault(category, CATEGORY_TO_GROUP_CODES.get(PlaceCategory.ETC));
    }

    /**
     * API 인증을 위한 HTTP 헤더를 생성한다.
     */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("Authorization", "KakaoAK " + kakaoProperties.clientId()); // Kakao API Key 설정
        return headers;
    }
}


