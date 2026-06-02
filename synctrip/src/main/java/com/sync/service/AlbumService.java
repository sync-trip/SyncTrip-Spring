package com.sync.service;

import com.sync.domain.album.AlbumPhoto;
import com.sync.domain.band.Band;
import com.sync.domain.user.User;
import com.sync.dto.album.AlbumPhotoMapResponse;
import com.sync.dto.album.AlbumPhotoResponse;
import com.sync.dto.album.AlbumPhotoUpdateRequest;
import com.sync.dto.album.AlbumPhotoUploadRequest;
import com.sync.repository.AlbumPhotoRepository;
import com.sync.repository.BandMemberRepository;
import com.sync.repository.BandRepository;
import com.sync.repository.UserRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AlbumService {

    // 사진 업로드 시 프론트가 보낸 EXIF 좌표·촬영시각을 확인하기 위한 진단 로거
    private static final Logger log = LoggerFactory.getLogger(AlbumService.class);

    private final AlbumPhotoRepository albumPhotoRepository;
    private final BandRepository bandRepository;
    private final BandMemberRepository bandMemberRepository;
    private final UserRepository userRepository;

    public AlbumService(AlbumPhotoRepository albumPhotoRepository,
                        BandRepository bandRepository,
                        BandMemberRepository bandMemberRepository,
                        UserRepository userRepository) {
        this.albumPhotoRepository = albumPhotoRepository;
        this.bandRepository = bandRepository;
        this.bandMemberRepository = bandMemberRepository;
        this.userRepository = userRepository;
    }

    /** 사진 업로드 — 밴드 멤버만 가능 */
    public AlbumPhotoResponse uploadPhoto(Long userId, Long bandId, AlbumPhotoUploadRequest request) {
        User user = findUser(userId);
        Band band = findBand(bandId);
        requireMember(bandId, userId);

        // [진단] 프론트가 EXIF에서 추출해 보낸 좌표·촬영시각·이미지 크기 기록
        // lat/lng가 null이면 EXIF 위치 미추출, 0.0이면 갤러리/포토피커의 위치 redact 의심
        log.info("[앨범 업로드] userId={}, bandId={}, latitude={}, longitude={}, takenAt={}, photoDataLength={}",
                userId, bandId, request.latitude(), request.longitude(), request.takenAt(),
                request.photoData() == null ? 0 : request.photoData().length());

        AlbumPhoto photo = AlbumPhoto.create(
                band, user,
                request.photoData(),
                request.caption(),
                request.latitude(),
                request.longitude(),
                request.takenAt()
        );
        albumPhotoRepository.save(photo);
        return AlbumPhotoResponse.from(photo);
    }

    /** 앨범 피드 목록 조회 — 최신순 */
    @Transactional(readOnly = true)
    public List<AlbumPhotoResponse> getPhotos(Long userId, Long bandId) {
        findUser(userId);
        findBand(bandId);
        requireMember(bandId, userId);

        return albumPhotoRepository
                .findByBandIdAndIsDeletedFalseOrderByUploadedAtDesc(bandId)
                .stream()
                .map(AlbumPhotoResponse::from)
                .collect(Collectors.toList());
    }

    /** 포스트 상세 조회 — 지도 핀 클릭 시 */
    @Transactional(readOnly = true)
    public AlbumPhotoResponse getPhotoDetail(Long userId, Long bandId, Long photoId) {
        findUser(userId);
        findBand(bandId);
        requireMember(bandId, userId);

        AlbumPhoto photo = findActivePhoto(photoId, bandId);
        return AlbumPhotoResponse.from(photo);
    }

    /** 지도용 목록 — 좌표 있는 사진만, Base64 제외한 경량 응답 */
    @Transactional(readOnly = true)
    public List<AlbumPhotoMapResponse> getMapPhotos(Long userId, Long bandId) {
        findUser(userId);
        findBand(bandId);
        requireMember(bandId, userId);

        return albumPhotoRepository
                .findByBandIdAndIsDeletedFalseOrderByUploadedAtDesc(bandId)
                .stream()
                .filter(p -> p.getLatitude() != null && p.getLongitude() != null)
                .map(AlbumPhotoMapResponse::from)
                .collect(Collectors.toList());
    }

    /** 글 수정 — 업로더 본인만 가능 */
    public AlbumPhotoResponse updateCaption(Long userId, Long bandId, Long photoId,
                                            AlbumPhotoUpdateRequest request) {
        findUser(userId);
        findBand(bandId);
        requireMember(bandId, userId);

        AlbumPhoto photo = findActivePhoto(photoId, bandId);
        if (!photo.getUploader().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인이 올린 사진만 수정할 수 있습니다.");
        }
        photo.updateCaption(request.caption());
        return AlbumPhotoResponse.from(photo);
    }

    /** 사진 삭제 — 업로더 본인 또는 방장만 가능 */
    public void deletePhoto(Long userId, Long bandId, Long photoId) {
        findUser(userId);
        Band band = findBand(bandId);
        requireMember(bandId, userId);

        AlbumPhoto photo = findActivePhoto(photoId, bandId);

        boolean isUploader = photo.getUploader().getId().equals(userId);
        boolean isOwner = band.getOwner().getId().equals(userId);
        if (!isUploader && !isOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인이 올린 사진 또는 방장만 삭제할 수 있습니다.");
        }
        photo.softDelete();
    }

    private AlbumPhoto findActivePhoto(Long photoId, Long bandId) {
        AlbumPhoto photo = albumPhotoRepository.findById(photoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사진을 찾을 수 없습니다."));
        if (photo.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사진을 찾을 수 없습니다.");
        }
        if (!photo.getBand().getId().equals(bandId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 밴드의 사진이 아닙니다.");
        }
        return photo;
    }

    private User findUser(Long userId) {
        return userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private Band findBand(Long bandId) {
        return bandRepository.findByIdAndIsDeletedFalse(bandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "밴드를 찾을 수 없습니다."));
    }

    private void requireMember(Long bandId, Long userId) {
        bandMemberRepository.findByBandIdAndUserId(bandId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "밴드 멤버만 접근할 수 있습니다."));
    }
}
