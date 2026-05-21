package com.sync.dto.notification;

import com.sync.domain.notification.Notification;
import com.sync.domain.notification.NotificationType;
import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        Long bandId,
        NotificationType type,
        String title,
        String content,
        boolean isRead,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getBand() != null ? n.getBand().getId() : null,
                n.getType(),
                n.getType().getTitle(),
                n.getContent(),
                n.isRead(),
                n.getCreatedAt()
        );
    }
}
