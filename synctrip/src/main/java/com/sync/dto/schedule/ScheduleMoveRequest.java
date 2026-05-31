package com.sync.dto.schedule;

/** 슬롯을 다른 Day로 이동하는 요청 — targetSlotOrder는 1-based */
public record ScheduleMoveRequest(
        Long scheduleId,
        int targetDayNumber,
        int targetSlotOrder
) {}
