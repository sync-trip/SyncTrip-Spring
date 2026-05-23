package com.sync.dto.album;

public record AlbumPhotoUpdateRequest(
        String caption  // 수정할 글 (null이면 빈 글로 초기화)
) {}
