package com.sync.domain.band;

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
 * 밴드 참여 멤버 엔티티
 * - 특정 사용자가 어떤 밴드에 어떤 역할(방장/멤버)로 참여 중인지 관리합니다.
 * - 멤버의 준비 상태(Ready) 및 장바구니 담기 개수 등을 추적합니다.
 */
@Entity
@Table(name = "group_members")
public class BandMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_member_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 회원 정보

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Band band; // 참여 중인 밴드

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private BandRole role; // 역할 (OWNER: 방장, MEMBER: 일반멤버)

    @Column(name = "is_ready", nullable = false)
    private boolean isReady = false; // 준비 완료 여부

    @Column(name = "bookmark_count", nullable = false)
    private int bookmarkCount = 0; // 장바구니에 담은 장소 개수

    @Column(name = "joined_after_voting", nullable = false)
    private boolean joinedAfterVoting = false; // 투표 시작 이후 합류 여부 (권한 제한용)

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false; // 탈퇴 여부 (Soft Delete)

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt; // 탈퇴 일시

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    protected BandMember() {
    }

    private BandMember(User user, Band band, BandRole role) {
        this.user = user;
        this.band = band;
        this.role = role;
    }

    /**
     * 새로운 멤버 객체 생성 (팩토리 메서드)
     */
    public static BandMember create(User user, Band band, BandRole role) {
        return new BandMember(user, band, role);
    }

    /**
     * 멤버의 준비 상태 수정
     */
    public void updateReady(boolean isReady) {
        this.isReady = isReady;
    }

    /**
     * 투표 단계 이후 합류했음을 마킹
     */
    public void markJoinedAfterVoting() {
        this.joinedAfterVoting = true;
    }

    /**
     * 멤버 탈퇴 처리 (Soft Delete)
     */
    public void delete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    // --- Getter 영역 ---
    public Long getId() { return id; }
    public User getUser() { return user; }
    public Band getBand() { return band; }
    public BandRole getRole() { return role; }
    public boolean isReady() { return isReady; }
    public int getBookmarkCount() { return bookmarkCount; }
    public boolean isJoinedAfterVoting() { return joinedAfterVoting; }
    public boolean isDeleted() { return isDeleted; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
}
