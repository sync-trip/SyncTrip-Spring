package com.sync.dto.place;

import com.sync.domain.place.PlaceApiSource;
import com.sync.domain.place.PlaceCategory;

/**
 * 모바일 앱이 받는 장소 검색 결과 DTO
 *
 * 용도:
 * - API 응답: GET /api/bands/{bandId}/places/search
 * - 프론트가 목록으로 표시하거나 북마크할 때 사용
 *
 * 구성:
 * - 백엔드 places 테이블에서 읽은 정보
 * - 현재 사용자의 북마크 여부
 * - 출처 정보 (Google vs Kakao)
 *
 * 흐름:
 * 1. PlaceSearchService.searchPlaces() 호출
 * 2. API(Google 또는 Kakao)에서 검색
 * 3. 결과를 Place 엔티티로 캐싱
 * 4. 이 DTO로 변환해서 모바일 앱에 반환
 */
public record PlaceSearchResult(
        // 백엔드 places 테이블의 primary key
        Long placeId,
        // 출처 (KAKAO 또는 GOOGLE)
        // 국내 여행이면 KAKAO, 해외 여행이면 GOOGLE
        PlaceApiSource apiSource,
        // 출처별 외부 ID (Kakao ID 또는 Google Place ID)
        // 같은 출처로 재검색하면 중복 저장 방지하는 키
        String externalId,
        // 장소명
        String name,
        // SyncTrip 카테고리 (FOOD, CULTURE, ACTIVITY, SHOPPING, NATURE, ETC)
        PlaceCategory category,
        // 위도
        double latitude,
        // 경도
        double longitude,
        // 주소 (도로명 또는 지번)
        String address,
        // 평점 (Google: 1~5, Kakao: 없을 수 있음)
        Float rating,
        // 썸네일 이미지 URL
        String thumbnailUrl,
        // 현재 로그인한 사용자가 이 장소를 북마크했는가?
        // 북마크 목록에서 이미 담긴 장소를 UI상 강조할 때 사용
        boolean isBookmarked
) {}
