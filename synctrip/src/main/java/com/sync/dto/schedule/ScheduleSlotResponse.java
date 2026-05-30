package com.sync.dto.schedule;

import java.time.LocalTime;

public record ScheduleSlotResponse(
        Long scheduleId,
        int slotOrder,
        LocalTime startTime,
        Integer durationMinutes,
        Integer travelTimeFromPrev,
        SchedulePlaceInfo place,
        // 알고리즘 경고 플래그
        boolean isOutlierCandidate,
        boolean openingHoursViolation,
        boolean mealWindowViolation,
        boolean lateSchedule,
        boolean openingHoursUnverified
) {}
