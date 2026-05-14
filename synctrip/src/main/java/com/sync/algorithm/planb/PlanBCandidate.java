package com.sync.algorithm.planb;

import com.sync.algorithm.PlaceCategory;

public record PlanBCandidate(
        long placeId,
        double recommendScore,
        PlaceCategory category,
        int estimatedDuration,
        double distanceKmToTarget,
        boolean fromOverflow           // true=Step2 overflow, false=altPool
) {}
