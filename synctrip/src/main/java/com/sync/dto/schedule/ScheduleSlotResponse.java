package com.sync.dto.schedule;

import java.time.LocalTime;

public record ScheduleSlotResponse(
        Long scheduleId,
        int slotOrder,
        LocalTime startTime,
        Integer durationMinutes,
        Integer travelTimeFromPrev,
        SchedulePlaceInfo place
) {}
