package com.sync.dto.schedule;

/** POST /schedule/add — altPool 장소를 특정 Day에 추가하는 요청 */
public record ScheduleAddRequest(Long placeId, int targetDayNumber) {}
