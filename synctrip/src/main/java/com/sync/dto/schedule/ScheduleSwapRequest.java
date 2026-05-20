package com.sync.dto.schedule;

/**
 * 일정 슬롯 교체 요청 DTO
 * - 기존 일정 슬롯(scheduleId)을 새로운 장소(newPlaceId)로 교체한다.
 */
public record ScheduleSwapRequest(
        Long scheduleId,
        Long newPlaceId
) {}
