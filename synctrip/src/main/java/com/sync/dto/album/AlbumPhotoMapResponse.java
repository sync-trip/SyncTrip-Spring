package com.sync.dto.album;

import com.sync.domain.album.AlbumPhoto;

/** 지도 핀 표시용 경량 응답 — photo_data(Base64) 제외 */
public record AlbumPhotoMapResponse(
        Long id,
        Double latitude,
        Double longitude,
        String uploaderName
) {
    public static AlbumPhotoMapResponse from(AlbumPhoto photo) {
        return new AlbumPhotoMapResponse(
                photo.getId(),
                photo.getLatitude(),
                photo.getLongitude(),
                photo.getUploader().getName()
        );
    }
}
