package com.sync.dto.place;

import com.sync.domain.place.PlaceApiSource;
import com.sync.domain.place.PlaceCategory;

public record PlaceSearchResult(
        Long placeId,
        PlaceApiSource apiSource,
        String externalId,
        String name,
        PlaceCategory category,
        double latitude,
        double longitude,
        String address,
        Float rating,
        String thumbnailUrl,
        boolean isBookmarked
) {}
