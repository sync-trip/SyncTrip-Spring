package com.sync.service;

import com.sync.domain.band.Band;
import com.sync.domain.place.PlaceCategory;
import com.sync.dto.place.PlaceSearchResult;
import com.sync.repository.BandRepository;
import com.sync.repository.PlaceBookmarkRepository;
import com.sync.repository.UserRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 장소 검색 서비스
 * - 요청 검증, 사용자별 북마크 매핑, 검색 위임을 담당한다.
 * - 실제 Google 검색·places 캐싱·결과 캐싱(Redis)은 {@link PlaceLookupService}가 수행한다.
 *   (@Cacheable이 self-invocation에서 동작하지 않으므로 캐싱 로직을 별도 빈으로 분리)
 * - 국내/해외 모두 Google Places Text Search를 사용한다.
 */
@Service
@Transactional
public class PlaceSearchService {

    private final BandRepository bandRepository;
    private final PlaceBookmarkRepository placeBookmarkRepository;
    private final UserRepository userRepository;
    private final PlaceLookupService placeLookupService;

    public PlaceSearchService(BandRepository bandRepository,
                              PlaceBookmarkRepository placeBookmarkRepository,
                              UserRepository userRepository,
                              PlaceLookupService placeLookupService) {
        this.bandRepository = bandRepository;
        this.placeBookmarkRepository = placeBookmarkRepository;
        this.userRepository = userRepository;
        this.placeLookupService = placeLookupService;
    }

    /**
     * 장소 검색 메인 메서드
     * 국내/해외 구분 없이 Google Places Text Search를 사용한다.
     *
     * @param userId   검색을 요청한 사용자 ID (북마크 여부 확인용)
     * @param bandId   검색 기준이 되는 밴드 ID (여행지 좌표 확인)
     * @param keyword  검색 키워드 (필수, 없으면 BAD_REQUEST)
     * @param category 검색할 장소 카테고리 (null인 경우 전체)
     * @return 검색된 장소 목록 (북마크 여부 포함)
     */
    @Transactional
    public List<PlaceSearchResult> searchPlaces(Long userId, Long bandId,
                                                String keyword,
                                                PlaceCategory category) {
        // 1. 요청 데이터 검증 (사용자 및 밴드 존재 여부)
        userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        Band band = bandRepository.findByIdAndIsDeletedFalse(bandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "밴드를 찾을 수 없습니다."));

        // 2. 키워드 검증 — 캐시 키(keyword.trim())에서 NPE가 나지 않도록 캐싱 위임 전에 수행
        if (keyword == null || keyword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "장소 검색은 키워드가 필요합니다.");
        }

        // 3. 현재 사용자가 해당 밴드에서 북마크한 장소 목록을 미리 조회 (결과에 마킹하기 위함)
        Set<Long> myBookmarkPlaceIds = placeBookmarkRepository
                .findByBandIdAndUserIdOrderByCreatedAtDesc(bandId, userId)
                .stream()
                .map(pb -> pb.getPlace().getId())
                .collect(Collectors.toSet());

        // 4. 검색 + 캐싱은 PlaceLookupService에 위임(Redis place-search 캐시). 결과는 북마크 미포함.
        List<PlaceSearchResult> cached = placeLookupService.searchAndCache(
                bandId, band.getDestinationLat(), band.getDestinationLng(), keyword, category);

        // 5. 사용자별 북마크 여부를 캐시 바깥에서 매핑
        return cached.stream()
                .map(r -> withBookmark(r, myBookmarkPlaceIds))
                .toList();
    }

    /**
     * 밴드 없이 위치 기반 숙소 검색 — 숙소 선택 등 밴드 생성 전 단계에서 사용.
     * TextSearch + rectangle restriction(50km)으로 반경 밖 결과를 완전히 제외한다.
     * keyword가 없으면 "hotel"로 기본 검색해 진입 시 숙소 목록을 자동 표시한다.
     * 북마크 컨텍스트가 없으므로 isBookmarked는 항상 false.
     *
     * @param userId   요청 사용자 ID (인증 확인용)
     * @param keyword  검색 키워드 (없으면 "hotel" 기본값으로 근처 숙소 목록 반환)
     * @param lat      검색 중심 위도
     * @param lng      검색 중심 경도
     */
    @Transactional
    public List<PlaceSearchResult> searchPlacesForLocation(Long userId, String keyword, double lat, double lng) {
        userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        return placeLookupService.searchByLocation(lat, lng, keyword);
    }

    /**
     * 캐시에서 받은 결과(북마크 미포함)에 사용자별 북마크 여부를 반영한다.
     * record는 불변이므로 북마크된 경우에만 isBookmarked=true로 새 인스턴스를 생성한다.
     */
    private PlaceSearchResult withBookmark(PlaceSearchResult r, Set<Long> bookmarkedIds) {
        if (!bookmarkedIds.contains(r.placeId())) return r;
        return new PlaceSearchResult(
                r.placeId(), r.apiSource(), r.externalId(), r.name(), r.category(),
                r.latitude(), r.longitude(), r.address(), r.rating(), r.thumbnailUrl(), true);
    }
}
