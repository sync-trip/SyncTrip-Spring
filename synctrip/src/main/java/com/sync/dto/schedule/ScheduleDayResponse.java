package com.sync.dto.schedule;

import java.time.LocalDate;
import java.util.List;

public record ScheduleDayResponse(
        int dayNumber,
        LocalDate date,
        List<ScheduleSlotResponse> slots
) {}
