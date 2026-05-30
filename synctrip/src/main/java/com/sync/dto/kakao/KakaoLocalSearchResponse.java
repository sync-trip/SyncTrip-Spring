package com.sync.dto.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Kakao Local API (카테고리 검색) 응답을 매핑하는 DTO
 *
 * Kakao API 엔드포인트:
 * - https://dapi.kakao.com/v2/local/search/category.json
 *
 * 사용 흐름:
 * 1. KakaoPlacesService에서 이 클래스로 응답 파싱
 * 2. documents에서 개별 Document를 추출
 * (장소 검색은 Google Places로 통일됨 — PlaceSearchService에서 미사용)
 *
 * 주의:
 * - @JsonIgnoreProperties(ignoreUnknown = true)로 미처리 필드는 무시
 * - x: 경도(longitude), y: 위도(latitude) - Google과 순서 반대임!
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoLocalSearchResponse(
        // 검색된 장소 목록
        List<Document> documents,
        // 검색 메타정보 (총 개수, 끝인지 여부 등)
        Meta meta
) {
    /**
     * 검색 결과의 개별 장소 정보
     *
     * 주요 필드:
     * - id: Kakao 내부 ID (unique)
     * - placeName: 장소명
     * - categoryName: "음식점", "문화시설" 등의 한글 분류
     * - categoryGroupCode: "FD6", "CT1" 등의 코드 분류 (검색에 사용)
     * - x, y: 좌표 (문자열, 반드시 double로 파싱 필요)
     * - roadAddressName: 도로명 주소
     * - addressName: 지번 주소
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(
            String id,
            @JsonProperty("place_name") String placeName,
            @JsonProperty("category_name") String categoryName,
            // Kakao 카테고리 그룹 코드 (FD6=음식점, CT1=문화, AT4=관광, SC4=쇼핑, PK6=공원)
            @JsonProperty("category_group_code") String categoryGroupCode,
            @JsonProperty("category_group_name") String categoryGroupName,
            @JsonProperty("address_name") String addressName,
            @JsonProperty("road_address_name") String roadAddressName,
            // 경도 (Longitude) - 문자열!
            String x,
            // 위도 (Latitude) - 문자열!
            String y,
            // 전화번호 (선택)
            String phone,
            @JsonProperty("place_url") String placeUrl,
            // 검색 기준점으로부터의 거리 (미터)
            String distance
    ) {}

    /**
     * 검색 결과 메타정보
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(
            // 검색 결과 총 개수
            @JsonProperty("total_count") int totalCount,
            // 이것이 마지막 페이지인가? (페이지네이션 미지원 시 항상 true)
            @JsonProperty("is_end") boolean isEnd
    ) {}
}
