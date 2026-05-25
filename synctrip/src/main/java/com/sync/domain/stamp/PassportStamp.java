package com.sync.domain.stamp;

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

@Entity
@Table(name = "passport_stamps")
public class PassportStamp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "passport_stamp_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Band band;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "country_code", length = 5)
    private String countryCode;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "stamped_at", nullable = false, updatable = false)
    private LocalDateTime stampedAt;

    protected PassportStamp() {}

    public static PassportStamp create(User user, Band band) {
        PassportStamp stamp = new PassportStamp();
        stamp.user = user;
        stamp.band = band;
        stamp.city = band.getDestination();
        stamp.country = band.getCountryCode();   // 국가명 없으므로 코드 재사용
        stamp.countryCode = band.getCountryCode();
        return stamp;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Band getBand() { return band; }
    public String getCity() { return city; }
    public String getCountry() { return country; }
    public String getCountryCode() { return countryCode; }
    public LocalDateTime getStampedAt() { return stampedAt; }
}
