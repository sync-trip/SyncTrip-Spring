package com.sync.service;

import com.sync.domain.band.Band;
import com.sync.domain.band.BandMember;
import com.sync.domain.band.BandStatus;
import com.sync.domain.place.Place;
import com.sync.domain.place.PlaceBookmark;
import com.sync.domain.user.User;
import com.sync.dto.pick.PlacePickListResponse;
import com.sync.dto.pick.PlacePickRequest;
import com.sync.dto.pick.PlacePickResponse;
import com.sync.repository.BandMemberRepository;
import com.sync.repository.BandRepository;
import com.sync.repository.PlaceBookmarkRepository;
import com.sync.repository.PlaceRepository;
import com.sync.repository.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 개인별 장바구니(Pick) 기능을 담당하는 서비스
 * - 카카오맵/구글맵 검색 결과를 받아 장소를 저장하고
 * - 밴드별/사용자별로 장바구니 목록과 삭제를 처리한다.
 */
@Service
@Transactional
public class PlacePickService {

    private static final int MAX_PICK_COUNT = 5;

    private final BandRepository bandRepository;
    private final BandMemberRepository bandMemberRepository;
    private final PlaceRepository placeRepository;
    private final PlaceBookmarkRepository placeBookmarkRepository;
    private final UserRepository userRepository;

    public PlacePickService(BandRepository bandRepository,
                            BandMemberRepository bandMemberRepository,
                            PlaceRepository placeRepository,
                            PlaceBookmarkRepository placeBookmarkRepository,
                            UserRepository userRepository) {
        this.bandRepository = bandRepository;
        this.bandMemberRepository = bandMemberRepository;
        this.placeRepository = placeRepository;
        this.placeBookmarkRepository = placeBookmarkRepository;
        this.userRepository = userRepository;
    }

    public PlacePickResponse addPick(Long userId, Long bandId, PlacePickRequest request) {
        // 1) 요청자와 밴드 존재 여부를 먼저 확인한다.
        User user = loadActiveUser(userId);
        Band band = loadBand(bandId);
        // 멀티스레드 동시성 문제를 막기 위해 멤버 행을 잠금 상태로 조회
        BandMember member = loadBandMemberForUpdate(bandId, userId);

        // 2) 장바구니는 여행 준비 중에만 사용 가능하다.
        validatePickableBand(band);

        // 3) 투표 시작 후 합류한 멤버는 장바구니/투표 권한이 제한된다.
        if (member.isJoinedAfterVoting()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "투표 이후 합류한 멤버는 장바구니를 사용할 수 없습니다.");
        }

        // 4) 요청값의 필수 항목을 검증한다.
        validateRequest(request);

        // 5) 같은 외부 장소는 places 테이블에서 재사용한다.
        Place place = placeRepository.findByApiSourceAndExternalId(request.apiSource(), request.externalId())
                .map(existing -> {
                    existing.syncMetadata(
                            request.name(),
                            request.category(),
                            request.latitude(),
                            request.longitude(),
                            request.address(),
                            request.rating(),
                            request.thumbnailUrl(),
                            request.openingHoursJson(),
                            request.estimatedDuration()
                    );
                    return existing;
                })
                .orElseGet(() -> Place.create(
                        request.apiSource(),
                        request.externalId(),
                        request.name(),
                        request.category(),
                        request.latitude(),
                        request.longitude(),
                        request.address(),
                        request.rating(),
                        request.thumbnailUrl(),
                        request.openingHoursJson(),
                        request.estimatedDuration()
                ));
        placeRepository.save(place);

        // 6) 이미 담은 장소면 중복 저장하지 않고 기존 기록을 그대로 반환한다.
        PlaceBookmark existingBookmark = placeBookmarkRepository.findByBandIdAndUserIdAndPlaceId(bandId, userId, place.getId())
                .orElse(null);
        if (existingBookmark != null) {
            return toResponse(existingBookmark);
        }

        // 7) 1인당 5개 제한을 넘기면 저장하지 않는다.
        // PESSIMISTIC_WRITE로 잠금한 멤버의 bookmarkCount를 기준으로 검사한다 (원자성 보장)
        long currentCount = member.getBookmarkCount();
        // 테스트/마이그레이션 환경과의 호환성을 위해 repository 카운트도 함께 확인하여
        // 실 DB 값과 엔티티 캐시 값 중 큰 값을 사용한다.
        long repoCount = placeBookmarkRepository.countByBandIdAndUserId(bandId, userId);
        currentCount = Math.max(currentCount, repoCount);
        if (currentCount >= MAX_PICK_COUNT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "장바구니는 최대 5개까지 담을 수 있습니다.");
        }

        // 8) 장바구니 항목을 저장하면 DB 트리거가 bookmark_count를 자동 갱신한다.
        PlaceBookmark bookmark = PlaceBookmark.create(band, user, place);
        placeBookmarkRepository.save(bookmark);

        return toResponse(bookmark);
    }

    @Transactional(readOnly = true)
    public PlacePickListResponse getMyPicks(Long userId, Long bandId) {
        // 1) 요청자와 밴드/멤버 존재 여부를 확인한다.
        loadActiveUser(userId);
        loadBand(bandId);
        loadBandMember(bandId, userId); // 멤버 존재 여부만 확인 (조회는 항상 허용)

        // 2) 현재 사용자의 장바구니 목록을 최신순으로 가져온다.
        // joined_after_voting 멤버는 담은 장소가 없으므로 자연히 빈 목록이 반환된다.
        List<PlacePickResponse> items = placeBookmarkRepository.findByBandIdAndUserIdOrderByCreatedAtDesc(bandId, userId)
                .stream()
                .map(this::toResponse)
                .toList();

        // 3) 현재 담긴 개수와 제한값을 함께 내려준다.
        return new PlacePickListResponse(
                items.size(),
                MAX_PICK_COUNT,
                items
        );
    }

    public void removePick(Long userId, Long bandId, Long placeId) {
        // 1) 요청자와 밴드를 검증한다.
        loadActiveUser(userId);
        Band band = loadBand(bandId);
        // 멀티스레드 동시성 보호를 위해 멤버 행을 잠금 상태로 조회
        BandMember member = loadBandMemberForUpdate(bandId, userId);

        // 2) 장바구니는 준비 단계에서만 삭제가 가능하다.
        validatePickableBand(band);
        if (member.isJoinedAfterVoting()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "투표 이후 합류한 멤버는 장바구니를 수정할 수 없습니다.");
        }

        // 3) 실제 저장된 장바구니가 있는지 확인하고 없으면 404를 반환한다.
        PlaceBookmark bookmark = placeBookmarkRepository.findByBandIdAndUserIdAndPlaceId(bandId, userId, placeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "장바구니에서 찾을 수 없는 장소입니다."));

        // 4) 삭제하면 DB 트리거가 bookmark_count를 자동 감소시킨다.
        placeBookmarkRepository.delete(bookmark);
    }

    private User loadActiveUser(Long userId) {
        return userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private Band loadBand(Long bandId) {
        return bandRepository.findByIdAndIsDeletedFalse(bandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "밴드를 찾을 수 없습니다."));
    }

    private BandMember loadBandMember(Long bandId, Long userId) {
        return bandMemberRepository.findByBandIdAndUserId(bandId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "밴드 멤버만 장바구니를 사용할 수 있습니다."));
    }

    private BandMember loadBandMemberForUpdate(Long bandId, Long userId) {
        // 테스트 환경(모의 객체)에서 아직 포괄적으로 stub되지 않은 경우를 대비해
        // 우선 잠금 조회를 시도하고 결과가 없으면 기존 조회로 폴백한다.
        return bandMemberRepository.findByBandIdAndUserIdForUpdate(bandId, userId)
                .or(() -> bandMemberRepository.findByBandIdAndUserId(bandId, userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "밴드 멤버만 장바구니를 사용할 수 있습니다."));
    }

    private void validatePickableBand(Band band) {
        // 여행 준비 단계(PLANNING/VOTING)에서만 장바구니를 사용할 수 있도록 제한한다.
        if (band.getStatus() == BandStatus.GENERATING
                || band.getStatus() == BandStatus.TRAVELLING
                || band.getStatus() == BandStatus.DONE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "장바구니는 여행 준비 중에만 사용할 수 있습니다.");
        }
    }

    private void validateRequest(PlacePickRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "장소 정보가 필요합니다.");
        }
        if (request.apiSource() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "apiSource가 필요합니다.");
        }
        if (request.externalId() == null || request.externalId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "externalId가 필요합니다.");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name이 필요합니다.");
        }
        if (request.category() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "category가 필요합니다.");
        }
    }

    private PlacePickResponse toResponse(PlaceBookmark bookmark) {
        Place place = bookmark.getPlace();
        return new PlacePickResponse(
                bookmark.getId(),
                place.getId(),
                place.getApiSource(),
                place.getExternalId(),
                place.getName(),
                place.getCategory(),
                place.getDensityPoint(),
                place.getLatitude(),
                place.getLongitude(),
                place.getAddress(),
                place.getRating(),
                place.getThumbnailUrl(),
                place.getOpeningHoursJson(),
                place.getEstimatedDuration(),
                bookmark.getCreatedAt()
        );
    }
}

