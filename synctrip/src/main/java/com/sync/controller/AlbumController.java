package com.sync.controller;

import com.sync.common.annotation.LoginUser;
import com.sync.dto.album.AlbumPhotoMapResponse;
import com.sync.dto.album.AlbumPhotoResponse;
import com.sync.dto.album.AlbumPhotoUpdateRequest;
import com.sync.dto.album.AlbumPhotoUploadRequest;
import com.sync.service.AlbumService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bands/{bandId}/album")
public class AlbumController {

    private final AlbumService albumService;

    public AlbumController(AlbumService albumService) {
        this.albumService = albumService;
    }

    /** POST /api/bands/{bandId}/album — 사진 + 글 + 좌표 업로드 */
    @PostMapping
    public ResponseEntity<AlbumPhotoResponse> uploadPhoto(
            @LoginUser Long userId,
            @PathVariable Long bandId,
            @Valid @RequestBody AlbumPhotoUploadRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(albumService.uploadPhoto(userId, bandId, request));
    }

    /** GET /api/bands/{bandId}/album — 피드 목록 (최신순, 전체) */
    @GetMapping
    public ResponseEntity<List<AlbumPhotoResponse>> getPhotos(
            @LoginUser Long userId,
            @PathVariable Long bandId
    ) {
        return ResponseEntity.ok(albumService.getPhotos(userId, bandId));
    }

    /** GET /api/bands/{bandId}/album/map — 지도 핀용 (좌표 있는 사진만, 경량) */
    @GetMapping("/map")
    public ResponseEntity<List<AlbumPhotoMapResponse>> getMapPhotos(
            @LoginUser Long userId,
            @PathVariable Long bandId
    ) {
        return ResponseEntity.ok(albumService.getMapPhotos(userId, bandId));
    }

    /** GET /api/bands/{bandId}/album/{photoId} — 포스트 상세 (지도 핀 클릭 시) */
    @GetMapping("/{photoId}")
    public ResponseEntity<AlbumPhotoResponse> getPhotoDetail(
            @LoginUser Long userId,
            @PathVariable Long bandId,
            @PathVariable Long photoId
    ) {
        return ResponseEntity.ok(albumService.getPhotoDetail(userId, bandId, photoId));
    }

    /** PATCH /api/bands/{bandId}/album/{photoId} — 글 수정 (업로더 본인만) */
    @PatchMapping("/{photoId}")
    public ResponseEntity<AlbumPhotoResponse> updateCaption(
            @LoginUser Long userId,
            @PathVariable Long bandId,
            @PathVariable Long photoId,
            @RequestBody AlbumPhotoUpdateRequest request
    ) {
        return ResponseEntity.ok(albumService.updateCaption(userId, bandId, photoId, request));
    }

    /** DELETE /api/bands/{bandId}/album/{photoId} — 삭제 (업로더 또는 방장) */
    @DeleteMapping("/{photoId}")
    public ResponseEntity<Void> deletePhoto(
            @LoginUser Long userId,
            @PathVariable Long bandId,
            @PathVariable Long photoId
    ) {
        albumService.deletePhoto(userId, bandId, photoId);
        return ResponseEntity.noContent().build();
    }
}
