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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "user_groups")
public class Band {
    // 초대코드는 72시간동안만 유효
    private static final long INVITE_CODE_TTL_HOURS = 72;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "title", nullable = false, length = 100)
    private String name;

    @Column(name = "destination", nullable = false, length = 100)
    private String destination;

    @Column(name = "destination_lat", nullable = false)
    private double destinationLat;

    @Column(name = "destination_lng", nullable = false)
    private double destinationLng;

    @Column(name = "country_code", nullable = false, length = 5)
    private String countryCode;

    @Column(name = "is_overseas", nullable = false)
    private boolean overseas;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "invite_code", nullable = false, unique = true, length = 20)
    private String inviteCode;

    @Column(name = "invite_code_expired_at", nullable = false)
    private LocalDateTime inviteCodeExpiredAt;

    @Column(name = "max_members", nullable = false)
    private int maxMembers = 8;

    @Enumerated(EnumType.STRING)
    @Column(name = "travel_style", nullable = false, length = 20)
    private TravelStyle travelStyle;

    @Column(name = "accommodation_name", length = 100)
    private String accommodationName;

    @Column(name = "accommodation_lat")
    private Double accommodationLat;

    @Column(name = "accommodation_lng")
    private Double accommodationLng;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BandStatus status;

    @Column(name = "closed_by", length = 20)
    private String closedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Band() {
    }

    // 모든 필드를 받는 생성자 (내부용)
    protected Band(User owner, String name, String destination, double destinationLat, double destinationLng,
                  String countryCode, boolean overseas, LocalDate startDate, LocalDate endDate,
                  String inviteCode, LocalDateTime inviteCodeExpiredAt, int maxMembers,
                  TravelStyle travelStyle, String accommodationName, Double accommodationLat,
                  Double accommodationLng, BandStatus status, String closedBy) {
        this.owner = owner;
        this.name = name;
        this.destination = destination;
        this.destinationLat = destinationLat;
        this.destinationLng = destinationLng;
        this.countryCode = countryCode;
        this.overseas = overseas;
        this.startDate = startDate;
        this.endDate = endDate;
        this.inviteCode = inviteCode;
        this.inviteCodeExpiredAt = inviteCodeExpiredAt;
        this.maxMembers = maxMembers;
        this.travelStyle = travelStyle;
        this.accommodationName = accommodationName;
        this.accommodationLat = accommodationLat;
        this.accommodationLng = accommodationLng;
        this.status = status;
        this.closedBy = closedBy;
    }

    // 기본 여행 정보로 생성 (팩토리 메서드)
    public static Band create(User owner, String name, String destination, double destinationLat,
                              double destinationLng, String countryCode, boolean overseas,
                              LocalDate startDate, LocalDate endDate) {
        return new Band(
                owner,
                name,
                destination,
                destinationLat,
                destinationLng,
                countryCode,
                overseas,
                startDate,
                endDate,
                generateInviteCode(),
                LocalDateTime.now().plusHours(INVITE_CODE_TTL_HOURS),
                8,
                TravelStyle.PACKED,
                null,
                null,
                null,
                BandStatus.PLANNING,
                null
        );
    }

    // 최소 정보만으로 생성 (이전 호환성용)
    public static Band createBasic(User owner, String name, LocalDate startDate, LocalDate endDate) {
        return new Band(
                owner,
                name,
                name,  // 임시로 name을 destination으로 사용
                0.0,
                0.0,
                "KR",
                false,
                startDate,
                endDate,
                generateInviteCode(),
                LocalDateTime.now().plusHours(INVITE_CODE_TTL_HOURS),
                8,
                TravelStyle.PACKED,
                null,
                null,
                null,
                BandStatus.PLANNING,
                null
        );
    }

    public static String generateInviteCode() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public void reissueInviteCode() {
        this.inviteCode = generateInviteCode();
        this.inviteCodeExpiredAt = LocalDateTime.now().plusHours(INVITE_CODE_TTL_HOURS);
    }

    public boolean isInviteCodeExpired(LocalDateTime now) {
        return inviteCodeExpiredAt == null || inviteCodeExpiredAt.isBefore(now);
    }

    public void updateStatus(BandStatus status) {
        this.status = status;
    }

    public void advanceStatus() {
        BandStatus nextStatus = this.status.next();
        if (nextStatus == null) {
            throw new IllegalStateException("더 이상 전이할 수 없는 밴드 상태입니다.");
        }
        this.status = nextStatus;
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getDestination() {
        return destination;
    }

    public double getDestinationLat() {
        return destinationLat;
    }

    public double getDestinationLng() {
        return destinationLng;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public boolean isOverseas() {
        return overseas;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public LocalDateTime getInviteCodeExpiredAt() {
        return inviteCodeExpiredAt;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public TravelStyle getTravelStyle() {
        return travelStyle;
    }

    public String getAccommodationName() {
        return accommodationName;
    }

    public Double getAccommodationLat() {
        return accommodationLat;
    }

    public Double getAccommodationLng() {
        return accommodationLng;
    }

    public BandStatus getStatus() {
        return status;
    }

    public String getClosedBy() {
        return closedBy;
    }
}
