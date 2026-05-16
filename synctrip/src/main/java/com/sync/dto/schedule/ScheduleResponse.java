package com.sync.dto.schedule;

import java.time.LocalDate;
import java.util.List;

public record ScheduleResponse(
        Long bandId,
        LocalDate startDate,
        LocalDate endDate,
        List<ScheduleDayResponse> days
) {}
