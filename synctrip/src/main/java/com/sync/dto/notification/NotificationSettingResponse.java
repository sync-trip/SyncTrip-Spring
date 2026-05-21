package com.sync.dto.notification;

import com.sync.domain.notification.NotificationType;
import com.sync.domain.user.User;

/**
 * 알림 수신 설정 조회 응답 DTO
 * GET /api/users/notification-settings
 *
 * 응답 예시:
 *   {
 *     "voteStarted": true,
 *     "scheduleUpdated": false,
 *     "settlementRequest": true,
 *     "memberReady": true
 *   }
 */
public record NotificationSettingResponse(
        boolean voteStarted,
        boolean scheduleUpdated,
        boolean settlementRequest,
        boolean memberReady,
        boolean memberJoined
) {
    public static NotificationSettingResponse from(User user) {
        return new NotificationSettingResponse(
                user.isNotificationEnabled(NotificationType.VOTE_STARTED),
                user.isNotificationEnabled(NotificationType.SCHEDULE_UPDATED),
                user.isNotificationEnabled(NotificationType.SETTLEMENT_REQUEST),
                user.isNotificationEnabled(NotificationType.MEMBER_READY),
                user.isNotificationEnabled(NotificationType.MEMBER_JOINED)
        );
    }
}
