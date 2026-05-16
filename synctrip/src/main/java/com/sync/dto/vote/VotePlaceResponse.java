package com.sync.dto.vote;

import com.sync.domain.place.PlaceApiSource;
import com.sync.domain.place.PlaceCategory;

public record VotePlaceResponse(
    Long placeId,
    PlaceApiSource apiSource,
    String name,
    PlaceCategory category,
    double latitude,
    double longitude,
    String address,
    Float rating,
    String thumbnailUrl,
    boolean myBookmark  // true이면 앱에서 자동 LIKE 처리
) {}
