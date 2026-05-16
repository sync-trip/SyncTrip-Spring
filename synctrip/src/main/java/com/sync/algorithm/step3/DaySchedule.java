package com.sync.algorithm.step3;

import java.util.List;

public record DaySchedule(
        int day,
        List<ScheduledPlace> places
) {}
