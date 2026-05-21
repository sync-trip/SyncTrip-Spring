package com.sync.dto.notification;

import com.sync.domain.notification.NotificationType;
import jakarta.validation.constraints.NotNull;

/**
 * 알림 수신 설정 변경 요청 DTO
 * PATCH /api/users/notification-settings
 *
 * 요청 예시:
 *   { "type": "VOTE_STARTED", "enabled": false }
 */
public record NotificationSettingRequest(
        @NotNull NotificationType type,
        @NotNull Boolean enabled
) {}
