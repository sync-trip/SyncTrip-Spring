package com.sync.dto.schedule;

/**
 * 슬롯을 다른 Day로 이동하는 요청 — targetSlotOrder는 1-based
 * - sendNotification: false면 이 요청에서 그룹 알림 생략 (연속 호출 시 마지막에만 true)
 *   JSON 필드명은 "notify" (@JsonProperty)
 */
public record ScheduleMoveRequest(
        Long scheduleId,
        int targetDayNumber,
        int targetSlotOrder,
        @com.fasterxml.jackson.annotation.JsonProperty("notify") Boolean sendNotification
) {
    /** sendNotification이 null이면 true(기본값)로 처리 */
    public boolean shouldNotify() {
        return sendNotification == null || sendNotification;
    }
}
