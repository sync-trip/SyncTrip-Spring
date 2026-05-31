package com.sync.dto.schedule;

import java.util.List;

/**
 * Drag & Drop 순서 변경 요청 DTO (USR-017)
 * - dayNumber: 재정렬할 일차
 * - orderedScheduleIds: 사용자가 지정한 새 순서의 scheduleId 목록
 * - sendNotification: false면 이 요청에서 그룹 알림 생략 (연속 호출 시 마지막에만 true)
 *   JSON 필드명은 "notify" (@JsonProperty)
 */
public record ScheduleReorderRequest(
        int dayNumber,
        List<Long> orderedScheduleIds,
        @com.fasterxml.jackson.annotation.JsonProperty("notify") Boolean sendNotification
) {
    /** sendNotification이 null이면 true(기본값)로 처리 */
    public boolean shouldNotify() {
        return sendNotification == null || sendNotification;
    }
}
