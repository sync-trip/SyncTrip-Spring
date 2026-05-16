package com.sync.dto.pick;

import com.sync.domain.place.PlaceApiSource;
import com.sync.domain.place.PlaceCategory;

/**
 * 앱에서 장바구니에 담을 장소 정보를 서버로 보낼 때 사용하는 요청 DTO
 * - 카카오맵/구글맵 검색 결과를 그대로 전달하면 된다.
 */
public record PlacePickRequest(
    PlaceApiSource apiSource,
    String externalId,
    String name,
    PlaceCategory category,
    double latitude,
    double longitude,
    String address,
    Float rating,
    String thumbnailUrl,
    String openingHoursJson,
    Integer estimatedDuration
) {
}

