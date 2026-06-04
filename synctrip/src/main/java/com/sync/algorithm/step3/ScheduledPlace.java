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
        boolean openingHoursViolation,
        boolean mealWindowViolation,        // TIME_OUT_OF_MEAL_WINDOW [작업2]
        boolean lateSchedule,               // 22:00 이후 시작 [작업2]
        boolean openingHoursUnverified      // 해외이고 영업시간 데이터 없음 [작업2]
) {}
