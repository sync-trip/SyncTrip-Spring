package com.sync.dto.album;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public record AlbumPhotoUploadRequest(
        @NotBlank String photoData,  // Base64 인코딩 문자열
        String caption,              // 사진 설명 글 (선택)
        Double latitude,             // GPS 위도 (선택, 프론트에서 EXIF 추출)
        Double longitude,            // GPS 경도 (선택, 프론트에서 EXIF 추출)
        LocalDateTime takenAt        // 촬영 시각 (선택, 프론트에서 EXIF 추출)
) {}
