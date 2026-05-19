package com.sync.dto.schedule;

import com.sync.domain.place.PlaceApiSource;
import com.sync.domain.place.PlaceCategory;

public record SchedulePlaceInfo(
        Long placeId,
        PlaceApiSource apiSource,
        String name,
        PlaceCategory category,
        double latitude,
        double longitude,
        String address,
        Float rating,
        String thumbnailUrl
) {}
