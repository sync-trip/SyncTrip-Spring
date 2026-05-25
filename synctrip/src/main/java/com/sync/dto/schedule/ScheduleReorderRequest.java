package com.sync.dto.schedule;

import java.util.List;

/**
 * Drag & Drop 순서 변경 요청 DTO (USR-017)
 * - dayNumber: 재정렬할 일차
 * - orderedScheduleIds: 사용자가 지정한 새 순서의 scheduleId 목록
 */
public record ScheduleReorderRequest(
        int dayNumber,
        List<Long> orderedScheduleIds
) {}
