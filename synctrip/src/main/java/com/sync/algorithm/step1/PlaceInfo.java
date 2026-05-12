package com.sync.algorithm.step1;

import com.sync.algorithm.PlaceCategory;

public record PlaceInfo(
        long placeId,
        long bookmarkedBy,
        String name,
        PlaceCategory category,
        int densityPoint,
        int estimatedDuration,
        double latitude,
        double longitude
) {}