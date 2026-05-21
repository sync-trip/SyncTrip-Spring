package com.sync.domain.notification;

import com.sync.domain.band.Band;
import com.sync.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 알림 엔티티 — notifications 테이블과 매핑
 *
 * 알림은 두 가지 경로로 전달됩니다:
 *   1. DB 저장 (이 엔티티) — 앱 내 알림 센터에서 목록 조회
 *   2. FCM 푸시 — 기기 잠금 화면·상단 알림바에 즉시 표시
 *
 * band는 nullable: 밴드와 무관한 시스템 알림도 지원하기 위함
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    // 알림 수신 대상 유저
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 알림과 연관된 밴드 (없으면 null)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Band band;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    // 알림 본문 (예: "홍길동 님이 준비 완료했어요!")
    @Column(name = "content", nullable = false, length = 255)
    private String content;

    // 읽음 여부 — 기본값 false, markRead() 호출 시 true
    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    // 알림 생성 시각 — DB INSERT 시 자동 설정
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Notification() {}

    public static Notification create(User user, Band band, NotificationType type, String content) {
        Notification n = new Notification();
        n.user = user;
        n.band = band;
        n.type = type;
        n.content = content;
        return n;
    }

    // 알림 1건을 읽음 처리 (PATCH /api/notifications/{id}/read)
    public void markRead() {
        this.isRead = true;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Band getBand() { return band; }
    public NotificationType getType() { return type; }
    public String getContent() { return content; }
    public boolean isRead() { return isRead; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
