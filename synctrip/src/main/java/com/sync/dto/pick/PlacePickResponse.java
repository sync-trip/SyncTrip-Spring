package com.sync.dto.pick;

import com.sync.domain.place.PlaceApiSource;
import com.sync.domain.place.PlaceCategory;
import java.time.LocalDateTime;

/**
 * 장바구니에 담긴 장소 1건을 내려주는 응답 DTO
 * - 프론트에서 카드형 리스트로 바로 렌더링할 수 있게 최소한의 정보를 모두 포함한다.
 */
public record PlacePickResponse(
    Long placeBookmarkId,
    Long placeId,
    PlaceApiSource apiSource,
    String externalId,
    String name,
    PlaceCategory category,
    int densityPoint,
    double latitude,
    double longitude,
    String address,
    Float rating,
    String thumbnailUrl,
    String openingHoursJson,
    int estimatedDuration,
    LocalDateTime createdAt
) {
}

