package com.sync.domain.user;

import com.sync.domain.notification.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "email")
    private String email;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "oauth_provider", nullable = false, length = 20)
    private OauthProvider oauthProvider;

    @Column(name = "oauth_id", nullable = false, length = 100)
    private String oauthId;

    @Column(name = "noti_vote_started", nullable = false)
    private boolean notiVoteStarted = true;

    @Column(name = "noti_schedule_updated", nullable = false)
    private boolean notiScheduleUpdated = true;

    @Column(name = "noti_settlement_request", nullable = false)
    private boolean notiSettlementRequest = true;

    @Column(name = "noti_member_ready", nullable = false)
    private boolean notiMemberReady = true;

    @Column(name = "noti_member_joined", nullable = false)
    private boolean notiMemberJoined = true;

    @Column(name = "fcm_token", length = 255)
    private String fcmToken;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected User() {
    }

    public User(String email, String name, String profileImageUrl, OauthProvider oauthProvider, String oauthId) {
        this.email = email;
        this.name = name;
        this.profileImageUrl = profileImageUrl;
        this.oauthProvider = oauthProvider;
        this.oauthId = oauthId;
    }

    public static User kakaoUser(String email, String name, String profileImageUrl, String oauthId) {
        return new User(email, name, profileImageUrl, OauthProvider.KAKAO, oauthId);
    }

    public static User googleUser(String email, String name, String profileImageUrl, String oauthId) {
        return new User(email, name, profileImageUrl, OauthProvider.GOOGLE, oauthId);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public OauthProvider getOauthProvider() {
        return oauthProvider;
    }

    public String getOauthId() {
        return oauthId;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    // 특정 알림 타입의 수신 여부 반환
    public boolean isNotificationEnabled(NotificationType type) {
        return switch (type) {
            case VOTE_STARTED        -> notiVoteStarted;
            case SCHEDULE_UPDATED    -> notiScheduleUpdated;
            case SETTLEMENT_REQUEST  -> notiSettlementRequest;
            case MEMBER_READY        -> notiMemberReady;
            case MEMBER_JOINED       -> notiMemberJoined;
        };
    }

    // 특정 알림 타입의 수신 여부 변경
    public void updateNotificationSetting(NotificationType type, boolean enabled) {
        switch (type) {
            case VOTE_STARTED        -> notiVoteStarted = enabled;
            case SCHEDULE_UPDATED    -> notiScheduleUpdated = enabled;
            case SETTLEMENT_REQUEST  -> notiSettlementRequest = enabled;
            case MEMBER_READY        -> notiMemberReady = enabled;
            case MEMBER_JOINED       -> notiMemberJoined = enabled;
        }
    }

    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public void updateProfile(String name, String profileImageUrl, String email) {
        this.name = name;
        this.profileImageUrl = profileImageUrl;
        this.email = email;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    // 회원탈퇴: Soft Delete (물리 삭제 대신 논리 삭제)
    public void withdraw() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    // 탈퇴 후 재가입: 계정 재활성화 (동일 oauth_id 제약조건 우회)
    public void reactivate(String name, String profileImageUrl, String email) {
        this.isDeleted = false;
        this.deletedAt = null;
        this.name = name;
        this.profileImageUrl = profileImageUrl;
        this.email = email;
    }
}

