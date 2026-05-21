package com.sync.dto.notification;

import com.sync.domain.notification.Notification;
import com.sync.domain.notification.NotificationType;
import java.time.LocalDateTime;

/**
 * 알림 조회 응답 DTO
 *
 * title은 DB에 저장하지 않고 NotificationType.getTitle()에서 동적으로 가져옵니다.
 * bandId가 null이면 밴드와 무관한 시스템 알림입니다.
 */
public record NotificationResponse(
        Long id,
        Long bandId,           // 관련 밴드 ID (없으면 null)
        NotificationType type, // 알림 종류 (VOTE_STARTED 등)
        String title,          // 푸시 알림 타이틀 (NotificationType의 한글 이름)
        String content,        // 알림 본문
        boolean isRead,        // 읽음 여부
        LocalDateTime createdAt
) {
    // Notification 엔티티 → 응답 DTO 변환
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
