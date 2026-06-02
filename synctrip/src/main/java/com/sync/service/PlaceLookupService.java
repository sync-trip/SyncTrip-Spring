package com.sync.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sync.domain.place.Place;
import com.sync.domain.place.PlaceApiSource;
import com.sync.domain.place.PlaceCategory;
import com.sync.dto.google.NearbySearchResponse;
import com.sync.dto.place.PlaceSearchResult;
import com.sync.repository.PlaceRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소 조회·캐싱 전담 서비스
 * - Google Places Text Search 호출 → places 테이블 영속화(FK 무결성) → DTO 변환을 담당한다.
 * - 검색 결과 목록은 Redis(place-search 캐시)에 캐싱하여 동일 검색의 Google API 재호출을 막는다.
 *
 * PlaceSearchService에서 분리한 이유:
 * - @Cacheable은 AOP 프록시 기반이라 같은 클래스 내부 호출(self-invocation)에는 적용되지 않는다.
 *   캐싱 대상 메서드를 별도 빈으로 분리해야 프록시를 거쳐 캐시가 동작한다.
 * - 캐시에는 사용자별 북마크 여부를 담지 않는다(사용자 간 공유되면 안 됨).
 *   따라서 여기서는 isBookmarked=false 고정으로 반환하고, 북마크 매핑은 PlaceSearchService가 캐시 바깥에서 수행한다.
 */
@Service
@Transactional
public class PlaceLookupService {

    private static final Logger log = LoggerFactory.getLogger(PlaceLookupService.class);

    // Google Places API에서 사용하는 타입들을 서비스 내부 카테고리(PlaceCategory)로 매핑하기 위한 집합
    // 음식점 관련 구글 타입
    private static final Set<String> FOOD_TYPES = Set.of(
            "restaurant", "cafe", "bakery", "bar", "fast_food_restaurant",
            "coffee_shop", "dessert_shop", "ice_cream_shop", "sandwich_shop",
            "pizza_restaurant", "sushi_restaurant", "ramen_restaurant",
            "korean_restaurant", "chinese_restaurant", "japanese_restaurant",
            "seafood_restaurant", "steak_house", "brunch_restaurant",
            "meal_delivery", "meal_takeaway", "food",
            // 2025-05-23 추가: New Places API 세부 음식점 타입
            "american_restaurant", "asian_restaurant", "brazilian_restaurant",
            "french_restaurant", "greek_restaurant", "hamburger_restaurant",
            "indian_restaurant", "italian_restaurant", "mediterranean_restaurant",
            "mexican_restaurant", "thai_restaurant", "turkish_restaurant",
            "vietnamese_restaurant", "vegan_restaurant", "vegetarian_restaurant",
            "food_court", "pub"
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
            "spa", "night_club", "stadium", "water_park", "theme_park",
            // 2025-05-23 추가: 스포츠/레저 시설 타입
            "fitness_center", "gym", "golf_course", "ski_resort",
            "swimming_pool", "tennis_court", "sports_club", "playground"
    );
    // 쇼핑 관련 구글 타입
    private static final Set<String> SHOPPING_TYPES = Set.of(
            "shopping_mall", "department_store", "clothing_store",
            "jewelry_store", "market", "gift_shop", "book_store",
            // 2025-05-23 추가: 세부 쇼핑 시설 타입
            "supermarket", "grocery_store", "convenience_store",
            "electronics_store", "shoe_store", "sporting_goods_store",
            "furniture_store", "pet_store", "toy_store", "florist"
    );
    // 자연/공원 관련 구글 타입
    private static final Set<String> NATURE_TYPES = Set.of(
            "park", "national_park", "beach", "campground",
            "botanical_garden", "garden", "nature_reserve", "hiking_area"
    );

    private final PlaceRepository placeRepository;
    private final GooglePlacesService googlePlacesService;
    private final ObjectMapper objectMapper;

    public PlaceLookupService(PlaceRepository placeRepository,
                              GooglePlacesService googlePlacesService,
                              ObjectMapper objectMapper) {
        this.placeRepository = placeRepository;
        this.googlePlacesService = googlePlacesService;
        this.objectMapper = objectMapper;
    }

    /**
     * 밴드 좌표 기준 키워드 장소 검색 + 결과 캐싱.
     * - Google Text Search 호출 → 카테고리 필터 → places 영속화 → DTO 변환.
     * - 결과 목록을 Redis place-search 캐시에 저장한다. 캐시 키: bandId:정규화키워드:카테고리.
     *   같은 밴드는 좌표가 고정이므로 bandId만으로 좌표를 대표할 수 있다.
     * - 북마크 여부는 캐시에 담지 않으므로 isBookmarked는 항상 false.
     *   (사용자별 북마크 매핑은 호출자가 캐시 바깥에서 수행)
     *
     * @param bandId   캐시 키 및 좌표 식별용 밴드 ID
     * @param lat      검색 중심 위도(밴드 목적지)
     * @param lng      검색 중심 경도(밴드 목적지)
     * @param keyword  검색 키워드 (호출 전 blank 검증 완료 가정 — 캐시 키에서 trim 사용)
     * @param category 카테고리 필터 (null이면 전체)
     */
    @Cacheable(value = "place-search",
            key = "#bandId + ':' + #keyword.trim().toLowerCase() + ':' + #category")
    public List<PlaceSearchResult> searchAndCache(Long bandId, double lat, double lng,
                                                  String keyword, PlaceCategory category) {
        NearbySearchResponse response = googlePlacesService.searchText(lat, lng, keyword, null);

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
                results.add(toSearchResult(place));
            } catch (Exception e) {
                log.warn("Google 장소 캐싱 실패 (externalId={}): {}", gPlace.id(), e.getMessage());
            }
        }
        return results;
    }

    /**
     * 위치 기반 숙소 검색 (밴드 생성 전 단계). 좌표 키 정밀도 이슈로 캐싱하지 않는다.
     * keyword가 없으면 "hotel"로 기본 검색해 근처 숙소 목록을 자동 표시한다.
     *
     * @param lat     검색 중심 위도
     * @param lng     검색 중심 경도
     * @param keyword 검색 키워드 (없으면 "hotel")
     */
    public List<PlaceSearchResult> searchByLocation(double lat, double lng, String keyword) {
        String textQuery = (keyword != null && !keyword.isBlank()) ? keyword : "hotel";
        NearbySearchResponse response = googlePlacesService.searchText(lat, lng, textQuery, "lodging");

        if (response.places() == null || response.places().isEmpty()) {
            return List.of();
        }

        List<PlaceSearchResult> results = new ArrayList<>();
        for (NearbySearchResponse.Place gPlace : response.places()) {
            try {
                Place place = cacheGooglePlace(gPlace);
                results.add(toSearchResult(place));
            } catch (Exception e) {
                log.warn("Google 장소 캐싱 실패 (externalId={}): {}", gPlace.id(), e.getMessage());
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
     * Place 엔티티를 클라이언트 반환용 DTO로 변환한다(북마크 미포함).
     * 북마크 여부는 캐시에 담지 않으므로 항상 false. 사용자별 매핑은 호출자가 수행한다.
     */
    private PlaceSearchResult toSearchResult(Place place) {
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
                false
        );
    }
}
