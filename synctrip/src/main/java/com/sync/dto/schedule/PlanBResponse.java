package com.sync.dto.schedule;

import com.sync.domain.place.PlaceCategory;

public record PlanBResponse(
        Long placeId,
        PlaceCategory category,
        double recommendScore,
        double distanceKmToTarget,
        boolean fromOverflow,
        SchedulePlaceInfo placeInfo
) {}
