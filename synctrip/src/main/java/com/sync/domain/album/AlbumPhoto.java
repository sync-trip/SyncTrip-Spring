package com.sync.domain.album;

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
@Table(name = "album_photos")
public class AlbumPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "album_photo_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Band band;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploader;

    @Column(name = "photo_data", nullable = false, columnDefinition = "LONGTEXT")
    private String photoData;

    @Column(name = "caption", columnDefinition = "TEXT")
    private String caption;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "taken_at")
    private LocalDateTime takenAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    protected AlbumPhoto() {}

    public static AlbumPhoto create(Band band, User uploader, String photoData,
                                    String caption, Double latitude, Double longitude,
                                    LocalDateTime takenAt) {
        AlbumPhoto photo = new AlbumPhoto();
        photo.band = band;
        photo.uploader = uploader;
        photo.photoData = photoData;
        photo.caption = caption;
        photo.latitude = latitude;
        photo.longitude = longitude;
        photo.takenAt = takenAt;
        return photo;
    }

    public void updateCaption(String caption) {
        this.caption = caption;
    }

    public void softDelete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Band getBand() { return band; }
    public User getUploader() { return uploader; }
    public String getPhotoData() { return photoData; }
    public String getCaption() { return caption; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public LocalDateTime getTakenAt() { return takenAt; }
    public boolean isDeleted() { return isDeleted; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
}
