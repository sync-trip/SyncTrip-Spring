package com.sync.dto.album;

import com.sync.domain.album.AlbumPhoto;
import java.time.LocalDateTime;

public record AlbumPhotoResponse(
        Long id,
        Long bandId,
        Long uploaderId,
        String uploaderName,
        String photoData,
        String caption,
        Double latitude,
        Double longitude,
        LocalDateTime takenAt,
        LocalDateTime uploadedAt
) {
    public static AlbumPhotoResponse from(AlbumPhoto photo) {
        return new AlbumPhotoResponse(
                photo.getId(),
                photo.getBand().getId(),
                photo.getUploader().getId(),
                photo.getUploader().getName(),
                photo.getPhotoData(),
                photo.getCaption(),
                photo.getLatitude(),
                photo.getLongitude(),
                photo.getTakenAt(),
                photo.getUploadedAt()
        );
    }
}
