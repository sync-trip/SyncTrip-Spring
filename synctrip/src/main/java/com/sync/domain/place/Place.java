package com.sync.domain.place;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 사용자가 장바구니에 담는 실제 장소 엔티티
 * - places 테이블과 매핑된다.
 * - 카카오맵/구글맵 검색 결과를 공통 구조로 저장한다.
 */
@Entity
@Table(name = "places")
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "api_source", nullable = false, length = 20)
    private PlaceApiSource apiSource;

    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private PlaceCategory category;

    @Column(name = "density_point", nullable = false)
    private int densityPoint;

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "rating")
    private Float rating;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Column(name = "opening_hours", columnDefinition = "json")
    private String openingHoursJson;

    @Column(name = "estimated_duration", nullable = false)
    private int estimatedDuration;

    protected Place() {
    }

    private Place(PlaceApiSource apiSource,
                  String externalId,
                  String name,
                  PlaceCategory category,
                  double latitude,
                  double longitude,
                  String address,
                  Float rating,
                  String thumbnailUrl,
                  String openingHoursJson,
                  Integer estimatedDuration) {
        this.apiSource = apiSource;
        this.externalId = externalId;
        this.name = name;
        this.category = category;
        this.densityPoint = category.densityPoint();
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
        this.rating = rating;
        this.thumbnailUrl = thumbnailUrl;
        this.openingHoursJson = openingHoursJson;
        this.estimatedDuration = estimatedDuration == null ? defaultDuration(category) : estimatedDuration;
    }

    public static Place create(PlaceApiSource apiSource,
                               String externalId,
                               String name,
                               PlaceCategory category,
                               double latitude,
                               double longitude,
                               String address,
                               Float rating,
                               String thumbnailUrl,
                               String openingHoursJson,
                               Integer estimatedDuration) {
        return new Place(apiSource, externalId, name, category, latitude, longitude, address, rating, thumbnailUrl, openingHoursJson, estimatedDuration);
    }

    public void syncMetadata(String name,
                             PlaceCategory category,
                             double latitude,
                             double longitude,
                             String address,
                             Float rating,
                             String thumbnailUrl,
                             String openingHoursJson,
                             Integer estimatedDuration) {
        // 같은 외부 장소 ID를 다시 받았을 때 최신 메타데이터로 갱신한다.
        this.name = name;
        this.category = category;
        this.densityPoint = category.densityPoint();
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
        this.rating = rating;
        this.thumbnailUrl = thumbnailUrl;
        this.openingHoursJson = openingHoursJson;
        this.estimatedDuration = estimatedDuration == null ? defaultDuration(category) : estimatedDuration;
    }

    private int defaultDuration(PlaceCategory category) {
        return switch (category) {
            case FOOD -> 60;
            case CULTURE, NATURE -> 90;
            case ACTIVITY -> 120;
            case SHOPPING, ETC -> 60;
        };
    }

    public Long getId() {
        return id;
    }

    public PlaceApiSource getApiSource() {
        return apiSource;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getName() {
        return name;
    }

    public PlaceCategory getCategory() {
        return category;
    }

    public int getDensityPoint() {
        return densityPoint;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getAddress() {
        return address;
    }

    public Float getRating() {
        return rating;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getOpeningHoursJson() {
        return openingHoursJson;
    }

    public int getEstimatedDuration() {
        return estimatedDuration;
    }
}

