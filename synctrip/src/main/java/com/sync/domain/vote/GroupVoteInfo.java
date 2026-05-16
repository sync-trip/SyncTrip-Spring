package com.sync.domain.vote;

import com.sync.domain.band.Band;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "group_vote_info")
public class GroupVoteInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_vote_info_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false, unique = true)
    private Band band;

    @Column(name = "vote_started_at")
    private LocalDateTime voteStartedAt;

    @Column(name = "vote_ended_at")
    private LocalDateTime voteEndedAt;

    @Column(name = "is_force_started", nullable = false)
    private boolean forceStarted = false;

    protected GroupVoteInfo() {}

    private GroupVoteInfo(Band band, boolean forceStarted) {
        this.band = band;
        this.voteStartedAt = LocalDateTime.now();
        this.forceStarted = forceStarted;
    }

    public static GroupVoteInfo start(Band band, boolean forceStarted) {
        return new GroupVoteInfo(band, forceStarted);
    }

    public void end() {
        this.voteEndedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Band getBand() { return band; }
    public LocalDateTime getVoteStartedAt() { return voteStartedAt; }
    public LocalDateTime getVoteEndedAt() { return voteEndedAt; }
    public boolean isForceStarted() { return forceStarted; }
}
