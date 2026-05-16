package com.sync.domain.place;

import com.sync.domain.band.Band;
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

/**
 * 밴드 안에서 한 사용자가 특정 장소를 장바구니에 담은 기록
 * - place_bookmarks 테이블과 매핑된다.
 * - 그룹별/사용자별 장바구니 중복 방지를 위해 unique key가 있다.
 */
@Entity
@Table(name = "place_bookmarks")
public class PlaceBookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_bookmark_id")
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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PlaceBookmark() {
    }

    private PlaceBookmark(Band band, User user, Place place) {
        this.band = band;
        this.user = user;
        this.place = place;
    }

    public static PlaceBookmark create(Band band, User user, Place place) {
        return new PlaceBookmark(band, user, place);
    }

    public Long getId() {
        return id;
    }

    public Band getBand() {
        return band;
    }

    public User getUser() {
        return user;
    }

    public Place getPlace() {
        return place;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

