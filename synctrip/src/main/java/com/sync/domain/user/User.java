package com.sync.domain.user;

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

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

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

    public boolean isDeleted() {
        return isDeleted;
    }

    public void updateProfile(String name, String profileImageUrl, String email) {
        this.name = name;
        this.profileImageUrl = profileImageUrl;
        this.email = email;
    }

    // 회원탈퇴: Soft Delete (물리 삭제 대신 논리 삭제)
    public void withdraw() {
        this.isDeleted = true;
    }
}

