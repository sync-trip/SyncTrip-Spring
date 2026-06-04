package com.sync.algorithm.step3;

import java.util.List;

public record DaySchedule(
        int day,
        List<ScheduledPlace> places,
        boolean dayOverloaded               // 마지막 슬롯 endTime > 22:00 [작업3]
) {}
