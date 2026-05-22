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

/**
 * 여행 그룹(밴드) 본체 엔티티
 * - 여행의 기본 정보(장소, 기간, 상태)를 관리합니다.
 * - Soft Delete를 지원하여 데이터 삭제 시 실제로 지우지 않고 마킹만 합니다.
 */
@Entity
@Table(name = "user_groups")
public class Band {
    private static final long INVITE_CODE_TTL_SECONDS = 30;
    private static final int EDIT_LOCK_TIMEOUT_MINUTES = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner; // 방장 (그룹 생성자)

    @Column(name = "title", nullable = false, length = 100)
    private String name; // 여행 제목

    @Column(name = "destination", nullable = false, length = 100)
    private String destination; // 목적지명

    @Column(name = "destination_lat", nullable = false)
    private double destinationLat; // 목적지 위도

    @Column(name = "destination_lng", nullable = false)
    private double destinationLng; // 목적지 경도

    @Column(name = "country_code", nullable = false, length = 5)
    private String countryCode; // 국가 코드 (예: KR, JP)

    @Column(name = "is_overseas", nullable = false)
    private boolean overseas; // 해외 여행 여부

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate; // 여행 시작일

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate; // 여행 종료일

    @Column(name = "invite_code", nullable = false, unique = true, length = 20)
    private String inviteCode; // 8자리 초대 코드

    @Column(name = "invite_code_expired_at", nullable = false)
    private LocalDateTime inviteCodeExpiredAt; // 초대 코드 만료 시간

    @Column(name = "max_members", nullable = false)
    private int maxMembers = 8; // 최대 인원 (기본 8명)

    @Enumerated(EnumType.STRING)
    @Column(name = "travel_style", nullable = false, length = 20)
    private TravelStyle travelStyle; // 여행 스타일 (RELAXED/PACKED)

    @Column(name = "accommodation_name", length = 100)
    private String accommodationName; // 숙소 이름 (선택)

    @Column(name = "accommodation_lat")
    private Double accommodationLat;

    @Column(name = "accommodation_lng")
    private Double accommodationLng;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BandStatus status; // 현재 진행 상태

    @Column(name = "closed_by", length = 20)
    private String closedBy; // 종료 주체

    @Column(name = "currently_editing_user_id")
    private Long currentlyEditingUserId;

    @Column(name = "last_editing_at")
    private LocalDateTime lastEditingAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false; // 삭제 여부 (Soft Delete)

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt; // 삭제 일시

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Band() {
    }

    private Band(User owner, String name, String destination, double destinationLat, double destinationLng,
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

    /**
     * 새로운 여행 밴드 생성 (정석 팩토리 메서드)
     */
    public static Band create(User owner, String name, String destination, double destinationLat,
                              double destinationLng, String countryCode, boolean overseas,
                              LocalDate startDate, LocalDate endDate, TravelStyle travelStyle,
                              String accommodationName, Double accommodationLat, Double accommodationLng) {
        return new Band(
                owner, name, destination, destinationLat, destinationLng,
                countryCode, overseas, startDate, endDate,
                generateInviteCode(),
                LocalDateTime.now().plusSeconds(INVITE_CODE_TTL_SECONDS),
                8, travelStyle, accommodationName, accommodationLat, accommodationLng,
                BandStatus.PLANNING, null
        );
    }

    /**
     * 8자리 무작위 대문자 초대 코드 생성
     */
    public static String generateInviteCode() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * 초대 코드 재발급 및 만료 시간 갱신 (72시간 연장)
     */
    public void reissueInviteCode() {
        this.inviteCode = generateInviteCode();
        this.inviteCodeExpiredAt = LocalDateTime.now().plusSeconds(INVITE_CODE_TTL_SECONDS);
    }

    /**
     * 초대 코드의 만료 여부 확인
     */
    public boolean isInviteCodeExpired(LocalDateTime now) {
        return inviteCodeExpiredAt == null || inviteCodeExpiredAt.isBefore(now);
    }

    /**
     * 밴드 상태를 다음 단계로 진행 (도메인 로직 보호)
     */
    public void advanceStatus() {
        BandStatus nextStatus = this.status.next();
        if (nextStatus == null) {
            throw new IllegalStateException("더 이상 진행할 수 없는 밴드 단계입니다.");
        }
        this.status = nextStatus;
    }

    /**
     * 여행 기본 정보 수정 (PLANNING 단계에서만 허용)
     */
    public void updateInfo(String name, String destination, double lat, double lng,
                          String countryCode, boolean overseas, LocalDate start, LocalDate end,
                          TravelStyle travelStyle, String accommodationName,
                          Double accommodationLat, Double accommodationLng) {
        if (this.status != BandStatus.PLANNING) {
            throw new IllegalStateException("여행 정보 수정은 준비 단계(PLANNING)에서만 가능합니다.");
        }
        this.name = name;
        this.destination = destination;
        this.destinationLat = lat;
        this.destinationLng = lng;
        this.countryCode = countryCode;
        this.overseas = overseas;
        this.startDate = start;
        this.endDate = end;
        if (travelStyle != null) {
            this.travelStyle = travelStyle;
        }
        this.accommodationName = accommodationName;
        this.accommodationLat = accommodationLat;
        this.accommodationLng = accommodationLng;
    }

    public void startEditing(Long userId) {
        this.currentlyEditingUserId = userId;
        this.lastEditingAt = LocalDateTime.now();
    }

    public void refreshEditingLock() {
        this.lastEditingAt = LocalDateTime.now();
    }

    public void finishEditing() {
        this.currentlyEditingUserId = null;
        this.lastEditingAt = null;
    }

    public boolean isEditingByOther(Long userId) {
        if (currentlyEditingUserId == null) return false;
        if (lastEditingAt != null && lastEditingAt.isBefore(LocalDateTime.now().minusMinutes(EDIT_LOCK_TIMEOUT_MINUTES))) {
            return false;
        }
        return !currentlyEditingUserId.equals(userId);
    }

    public void delete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    // --- Getter 영역 ---

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public String getName() { return name; }
    public String getDestination() { return destination; }
    public double getDestinationLat() { return destinationLat; }
    public double getDestinationLng() { return destinationLng; }
    public String getCountryCode() { return countryCode; }
    public boolean isOverseas() { return overseas; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getInviteCode() { return inviteCode; }
    public LocalDateTime getInviteCodeExpiredAt() { return inviteCodeExpiredAt; }
    public int getMaxMembers() { return maxMembers; }
    public TravelStyle getTravelStyle() { return travelStyle; }
    public String getAccommodationName() { return accommodationName; }
    public Double getAccommodationLat() { return accommodationLat; }
    public Double getAccommodationLng() { return accommodationLng; }
    public BandStatus getStatus() { return status; }
    public String getClosedBy() { return closedBy; }
    public boolean isDeleted() { return isDeleted; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public Long getCurrentlyEditingUserId() { return currentlyEditingUserId; }
    public LocalDateTime getLastEditingAt() { return lastEditingAt; }
}
