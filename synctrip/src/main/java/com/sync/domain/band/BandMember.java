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

@Entity
@Table(name = "group_members")
public class BandMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_member_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Band band;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private BandRole role;

    @Column(name = "is_ready", nullable = false)
    private boolean isReady = false;

    @Column(name = "bookmark_count", nullable = false)
    private int bookmarkCount = 0;

    @Column(name = "joined_after_voting", nullable = false)
    private boolean joinedAfterVoting = false;

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

    public static BandMember create(User user, Band band, BandRole role) {
        return new BandMember(user, band, role);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Band getBand() {
        return band;
    }

    public BandRole getRole() {
        return role;
    }

    public boolean isReady() {
        return isReady;
    }

    public int getBookmarkCount() {
        return bookmarkCount;
    }

    public boolean isJoinedAfterVoting() {
        return joinedAfterVoting;
    }

    public void updateReady(boolean isReady) {
        this.isReady = isReady;
    }
}
