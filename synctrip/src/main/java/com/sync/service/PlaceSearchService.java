package com.sync.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sync.domain.band.Band;
import com.sync.domain.place.Place;
import com.sync.domain.place.PlaceApiSource;
import com.sync.domain.place.PlaceCategory;
import com.sync.dto.google.NearbySearchResponse;
import com.sync.dto.kakao.KakaoLocalSearchResponse;
import com.sync.dto.place.PlaceSearchResult;
import com.sync.repository.BandRepository;
import com.sync.repository.PlaceBookmarkRepository;
import com.sync.repository.PlaceRepository;
import com.sync.repository.UserRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 장소 검색 서비스
 * - 국내(카카오) 및 해외(구글) API를 통합하여 주변 장소 검색 기능을 제공한다.
 * - 검색된 장소는 데이터베이스(places 테이블)에 캐싱하여 관리한다.
 */
@Service
@Transactional
public class PlaceSearchService {

    private static final Logger log = LoggerFactory.getLogger(PlaceSearchService.class);
    // 기본 검색 반경 (5000미터 = 5km)
    private static final double DEFAULT_RADIUS_METERS = 5000;

    // Google Places API에서 사용하는 타입들을 서비스 내부 카테고리(PlaceCategory)로 매핑하기 위한 집합
    // 음식점 관련 구글 타입
    private static final Set<String> FOOD_TYPES = Set.of(
            "restaurant", "cafe", "bakery", "bar", "fast_food_restaurant",
            "coffee_shop", "dessert_shop", "ice_cream_shop", "sandwich_shop",
            "pizza_restaurant", "sushi_restaurant", "ramen_restaurant",
            "korean_restaurant", "chinese_restaurant", "japanese_restaurant",
            "seafood_restaurant", "steak_house", "brunch_restaurant",
            "meal_delivery", "meal_takeaway", "food"
    );
    // 문화 시설 관련 구글 타입
    // point_of_interest: 관광 명소/랜드마크처럼 검색되지만 세부 타입이 없는 장소의 fallback
    private static final Set<String> CULTURE_TYPES = Set.of(
            "museum", "art_gallery", "library", "movie_theater",
            "performing_arts_theater", "cultural_center", "historical_landmark",
            "monument", "palace", "tourist_attraction", "point_of_interest"
    );
    // 액티비티/오락 관련 구글 타입
    private static final Set<String> ACTIVITY_TYPES = Set.of(
            "amusement_park", "zoo", "aquarium", "bowling_alley",
            "spa", "night_club", "stadium", "water_park", "theme_park"
    );
    // 쇼핑 관련 구글 타입
    private static final Set<String> SHOPPING_TYPES = Set.of(
            "shopping_mall", "department_store", "clothing_store",
            "jewelry_store", "market", "gift_shop", "book_store"
    );
    // 자연/공원 관련 구글 타입
    private static final Set<String> NATURE_TYPES = Set.of(
            "park", "national_park", "beach", "campground",
            "botanical_garden", "garden", "nature_reserve", "hiking_area"
    );

    private final BandRepository bandRepository;
    private final PlaceRepository placeRepository;
    private final PlaceBookmarkRepository placeBookmarkRepository;
    private final UserRepository userRepository;
    private final KakaoPlacesService kakaoPlacesService;
    private final GooglePlacesService googlePlacesService;
    private final ObjectMapper objectMapper;

    public PlaceSearchService(BandRepository bandRepository,
                               PlaceRepository placeRepository,
                               PlaceBookmarkRepository placeBookmarkRepository,
                               UserRepository userRepository,
                               KakaoPlacesService kakaoPlacesService,
                               GooglePlacesService googlePlacesService,
                               ObjectMapper objectMapper) {
        this.bandRepository = bandRepository;
        this.placeRepository = placeRepository;
        this.placeBookmarkRepository = placeBookmarkRepository;
        this.userRepository = userRepository;
        this.kakaoPlacesService = kakaoPlacesService;
        this.googlePlacesService = googlePlacesService;
        this.objectMapper = objectMapper;
    }

    /**
     * 주변 장소 검색 메인 메서드
     * 밴드의 설정(국내/해외)에 따라 적절한 API를 호출한다.
     *
     * @param userId       검색을 요청한 사용자 ID (북마크 여부 확인용)
     * @param bandId       검색 기준이 되는 밴드 ID (여행지 좌표 및 국내/해외 여부 확인)
     * @param category     검색할 장소 카테고리 (null인 경우 전체)
     * @param radiusMeters 검색 반경 (단위: 미터)
     * @return 검색된 장소 목록 (북마크 여부 포함)
     */
    @Transactional
    public List<PlaceSearchResult> searchPlaces(Long userId, Long bandId,
                                                 String keyword,
                                                 PlaceCategory category,
                                                 double radiusMeters) {
        // 1. 요청 데이터 검증 (사용자 및 밴드 존재 여부)
        userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        Band band = bandRepository.findByIdAndIsDeletedFalse(bandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "밴드를 찾을 수 없습니다."));

        // 2. 현재 사용자가 해당 밴드에서 북마크한 장소 목록을 미리 조회 (결과에 마킹하기 위함)
        Set<Long> myBookmarkPlaceIds = placeBookmarkRepository
                .findByBandIdAndUserIdOrderByCreatedAtDesc(bandId, userId)
                .stream()
                .map(pb -> pb.getPlace().getId())
                .collect(Collectors.toSet());

        // 3. 밴드의 국가 설정에 따라 국내/해외 검색 분기 처리
        if (band.isOverseas()) {
            return searchOverseas(band, keyword, category, radiusMeters, myBookmarkPlaceIds);
        } else {
            return searchDomestic(band, category, radiusMeters, myBookmarkPlaceIds);
        }
    }

    /**
     * 해외 장소 검색 (Google Places API 활용)
     * - Text Search로 키워드 검색 후 서버 사이드에서 카테고리 필터링
     * - includedTypes는 TextSearch API 미지원 (NearbySearch 전용) → 응답 후 필터링으로 처리
     */
    private List<PlaceSearchResult> searchOverseas(Band band, String keyword,
                                                    PlaceCategory category,
                                                    double radiusMeters,
                                                    Set<Long> myBookmarkPlaceIds) {
        if (keyword == null || keyword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "해외 장소 검색은 키워드가 필요합니다.");
        }
        NearbySearchResponse response = googlePlacesService.searchText(
                band.getDestinationLat(), band.getDestinationLng(), keyword
        );

        if (response.places() == null || response.places().isEmpty()) {
            return List.of();
        }

        List<PlaceSearchResult> results = new ArrayList<>();
        for (NearbySearchResponse.Place gPlace : response.places()) {
            try {
                // 카테고리 필터: 응답 장소의 타입을 매핑한 뒤 요청 카테고리와 비교
                if (category != null && mapGoogleTypes(gPlace.types()) != category) {
                    continue;
                }
                Place place = cacheGooglePlace(gPlace);
                results.add(toSearchResult(place, myBookmarkPlaceIds));
            } catch (Exception e) {
                log.warn("Google 장소 캐싱 실패 (externalId={}): {}", gPlace.id(), e.getMessage());
            }
        }
        return results;
    }

    /**
     * 국내 장소 검색 (Kakao Local API 활용)
     */
    private List<PlaceSearchResult> searchDomestic(Band band, PlaceCategory category,
                                                    double radiusMeters,
                                                    Set<Long> myBookmarkPlaceIds) {
        // 카카오 검색 호출
        List<KakaoLocalSearchResponse.Document> docs = kakaoPlacesService.searchNearby(
                band.getDestinationLat(), band.getDestinationLng(),
                radiusMeters, category
        );

        if (docs == null || docs.isEmpty()) {
            return List.of();
        }

        List<PlaceSearchResult> results = new ArrayList<>();
        for (KakaoLocalSearchResponse.Document doc : docs) {
            try {
                // 검색된 장소를 DB에 캐싱하고 DTO로 변환
                Place place = cacheKakaoPlace(doc, category);
                results.add(toSearchResult(place, myBookmarkPlaceIds));
            } catch (Exception e) {
                log.warn("Kakao 장소 캐싱 실패 (externalId={}): {}", doc.id(), e.getMessage());
            }
        }
        return results;
    }

    /**
     * 구글 장소 정보를 데이터베이스에 저장(캐싱)한다.
     * 이미 존재하는 경우 최신 정보로 업데이트한다.
     */
    private Place cacheGooglePlace(NearbySearchResponse.Place gPlace) {
        String openingHoursJson = convertOpeningHours(gPlace.regularOpeningHours());
        String thumbnailUrl = buildGoogleThumbnailUrl(gPlace);
        PlaceCategory category = mapGoogleTypes(gPlace.types());
        Float rating = gPlace.rating() != null ? gPlace.rating().floatValue() : null;
        String name = gPlace.displayName() != null ? gPlace.displayName().text() : "알 수 없는 장소";
        double lat = gPlace.location() != null ? gPlace.location().latitude() : 0;
        double lng = gPlace.location() != null ? gPlace.location().longitude() : 0;

        return placeRepository.findByApiSourceAndExternalId(PlaceApiSource.GOOGLE, gPlace.id())
                .map(existing -> {
                    // 메타데이터 동기화
                    existing.syncMetadata(name, category, lat, lng,
                            gPlace.formattedAddress(), rating, thumbnailUrl, openingHoursJson, null);
                    return placeRepository.save(existing);
                })
                .orElseGet(() -> placeRepository.save(Place.create(
                        PlaceApiSource.GOOGLE, gPlace.id(), name, category,
                        lat, lng, gPlace.formattedAddress(), rating,
                        thumbnailUrl, openingHoursJson, null
                )));
    }

    /**
     * 카카오 장소 정보를 데이터베이스에 저장(캐싱)한다.
     */
    private Place cacheKakaoPlace(KakaoLocalSearchResponse.Document doc, PlaceCategory requestedCategory) {
        // 좌표 파싱 (카카오는 문자열로 반환함)
        double lat = Double.parseDouble(doc.y());
        double lng = Double.parseDouble(doc.x());
        // 도로명 주소 우선, 없으면 지번 주소 사용
        String address = (doc.roadAddressName() != null && !doc.roadAddressName().isEmpty())
                ? doc.roadAddressName() : doc.addressName();

        // 요청한 카테고리가 있으면 유지, 없으면 기타로 처리
        PlaceCategory category = requestedCategory != null ? requestedCategory : PlaceCategory.ETC;

        return placeRepository.findByApiSourceAndExternalId(PlaceApiSource.KAKAO, doc.id())
                .map(existing -> {
                    existing.syncMetadata(doc.placeName(), category, lat, lng,
                            address, null, null, null, null);
                    return placeRepository.save(existing);
                })
                .orElseGet(() -> placeRepository.save(Place.create(
                        PlaceApiSource.KAKAO, doc.id(), doc.placeName(), category,
                        lat, lng, address, null,
                        null, null, null
                )));
    }

    /**
     * 구글의 사진 정보를 실제 접근 가능한 썸네일 URL로 변환한다.
     */
    private String buildGoogleThumbnailUrl(NearbySearchResponse.Place gPlace) {
        if (gPlace.photos() == null || gPlace.photos().isEmpty()) return null;
        return googlePlacesService.buildPhotoUrl(gPlace.photos().get(0).name());
    }

    /**
     * 구글의 영업 시간 객체를 서비스에서 관리하기 쉬운 JSON 문자열로 변환한다.
     */
    private String convertOpeningHours(NearbySearchResponse.RegularOpeningHours hours) {
        if (hours == null || hours.periods() == null) return null;

        String[] dayKeys = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
        Map<String, List<Map<String, String>>> result = new HashMap<>();

        for (NearbySearchResponse.RegularOpeningHours.Period period : hours.periods()) {
            if (period.open() == null) continue;
            String day = dayKeys[period.open().day()];
            String open = String.format("%02d:%02d", period.open().hour(), period.open().minute());
            String close = period.close() != null
                    ? String.format("%02d:%02d", period.close().hour(), period.close().minute())
                    : "00:00";

            result.computeIfAbsent(day, k -> new ArrayList<>())
                    .add(Map.of("open", open, "close", close));
        }

        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            log.warn("opening_hours JSON 변환 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 구글의 장소 타입 리스트를 서비스 내부 카테고리 하나로 매핑한다.
     * 모든 타입을 먼저 스캔한 뒤 FOOD > CULTURE > ACTIVITY > SHOPPING > NATURE 우선순위로 반환.
     * (per-type 조기 반환 방식을 사용하면 point_of_interest 추가 시 음식점이 CULTURE로 오분류됨)
     */
    private PlaceCategory mapGoogleTypes(List<String> types) {
        if (types == null || types.isEmpty()) return PlaceCategory.ETC;
        boolean hasFood = false, hasCulture = false, hasActivity = false,
                hasShopping = false, hasNature = false;
        for (String type : types) {
            if (FOOD_TYPES.contains(type))     hasFood = true;
            if (CULTURE_TYPES.contains(type))  hasCulture = true;
            if (ACTIVITY_TYPES.contains(type)) hasActivity = true;
            if (SHOPPING_TYPES.contains(type)) hasShopping = true;
            if (NATURE_TYPES.contains(type))   hasNature = true;
        }
        if (hasFood)     return PlaceCategory.FOOD;
        if (hasCulture)  return PlaceCategory.CULTURE;
        if (hasActivity) return PlaceCategory.ACTIVITY;
        if (hasShopping) return PlaceCategory.SHOPPING;
        if (hasNature)   return PlaceCategory.NATURE;
        return PlaceCategory.ETC;
    }

    /**
     * Place 엔티티를 클라이언트 반환용 DTO로 변환한다.
     */
    private PlaceSearchResult toSearchResult(Place place, Set<Long> myBookmarkPlaceIds) {
        return new PlaceSearchResult(
                place.getId(),
                place.getApiSource(),
                place.getExternalId(),
                place.getName(),
                place.getCategory(),
                place.getLatitude(),
                place.getLongitude(),
                place.getAddress(),
                place.getRating(),
                place.getThumbnailUrl(),
                myBookmarkPlaceIds.contains(place.getId())
        );
    }
}

