package com.sync.algorithm.step3;

import com.sync.algorithm.PlaceCategory;

import java.time.LocalTime;

public record ScheduledPlace(
        long placeId,
        int day,
        int orderInDay,
        PlaceCategory category,
        double latitude,
        double longitude,
        int estimatedDuration,
        LocalTime startTime,
        LocalTime endTime,
        double priorityScore,
        boolean isOutlierCandidate,
        boolean openingHoursViolation
) {}
