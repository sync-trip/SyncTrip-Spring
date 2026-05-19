package com.sync.dto.schedule;

import com.sync.domain.place.PlaceCategory;

public record ScheduleAltResponse(
        Long scheduleAltId,
        PlaceCategory category,
        float priorityScore,
        SchedulePlaceInfo place
) {}
