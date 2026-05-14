package com.sync.algorithm.step2;

import com.sync.algorithm.PlaceCategory;

public record AssignedPlace(
        long placeId,
        int day,
        PlaceCategory category,
        double latitude,
        double longitude,
        int estimatedDuration,
        double priorityScore,
        boolean isOutlierCandidate
) {}
