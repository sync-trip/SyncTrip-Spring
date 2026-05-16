package com.sync.domain.vote;

import com.sync.domain.band.Band;
import com.sync.domain.place.Place;
import com.sync.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "votes")
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vote_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Band band;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    // 1=LIKE, -1=DISLIKE, 0=자동LIKE(본인 북마크 장소)
    @Column(name = "result", nullable = false)
    private int result;

    @CreationTimestamp
    @Column(name = "voted_at", nullable = false, updatable = false)
    private LocalDateTime votedAt;

    protected Vote() {}

    private Vote(Band band, User user, Place place, int result) {
        this.band = band;
        this.user = user;
        this.place = place;
        this.result = result;
    }

    public static Vote create(Band band, User user, Place place, int result) {
        return new Vote(band, user, place, result);
    }

    public Long getId() { return id; }
    public Band getBand() { return band; }
    public User getUser() { return user; }
    public Place getPlace() { return place; }
    public int getResult() { return result; }
    public LocalDateTime getVotedAt() { return votedAt; }
}
