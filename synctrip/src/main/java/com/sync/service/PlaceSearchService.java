package com.sync.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sync.domain.band.Band;
import com.sync.domain.place.Place;
import com.sync.domain.place.PlaceApiSource;
import com.sync.domain.place.PlaceCategory;
import com.sync.dto.google.NearbySearchResponse;
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

@Service
@Transactional
public class PlaceSearchService {

    private static final Logger log = LoggerFactory.getLogger(PlaceSearchService.class);
    private static final double DEFAULT_RADIUS_METERS = 5000;

    // Google place types → PlaceCategory 매핑
    private static final Set<String> FOOD_TYPES = Set.of(
            "restaurant", "cafe", "bakery", "bar", "fast_food_restaurant",
            "coffee_shop", "dessert_shop", "ice_cream_shop", "sandwich_shop",
            "pizza_restaurant", "sushi_restaurant", "ramen_restaurant",
            "korean_restaurant", "chinese_restaurant", "japanese_restaurant",
            "seafood_restaurant", "steak_house", "brunch_restaurant",
            "meal_delivery", "meal_takeaway", "food"
    );
    private static final Set<String> CULTURE_TYPES = Set.of(
            "museum", "art_gallery", "library", "movie_theater",
            "performing_arts_theater", "cultural_center", "historical_landmark",
            "monument", "palace", "tourist_attraction"
    );
    private static final Set<String> ACTIVITY_TYPES = Set.of(
            "amusement_park", "zoo", "aquarium", "bowling_alley",
            "spa", "night_club", "stadium", "water_park", "theme_park"
    );
    private static final Set<String> SHOPPING_TYPES = Set.of(
            "shopping_mall", "department_store", "clothing_store",
            "jewelry_store", "market", "gift_shop", "book_store"
    );
    private static final Set<String> NATURE_TYPES = Set.of(
            "park", "national_park", "beach", "campground",
            "botanical_garden", "garden", "nature_reserve", "hiking_area"
    );

    // PlaceCategory → Google includedTypes (검색 시 사용)
    private static final Map<PlaceCategory, List<String>> CATEGORY_TO_GOOGLE_TYPES = new HashMap<>();
    static {
        CATEGORY_TO_GOOGLE_TYPES.put(PlaceCategory.FOOD,
                List.of("restaurant", "cafe", "bakery", "bar", "fast_food_restaurant"));
        CATEGORY_TO_GOOGLE_TYPES.put(PlaceCategory.CULTURE,
                List.of("museum", "art_gallery", "historical_landmark", "movie_theater", "tourist_attraction"));
        CATEGORY_TO_GOOGLE_TYPES.put(PlaceCategory.ACTIVITY,
                List.of("amusement_park", "zoo", "aquarium", "bowling_alley", "spa"));
        CATEGORY_TO_GOOGLE_TYPES.put(PlaceCategory.SHOPPING,
                List.of("shopping_mall", "department_store", "clothing_store", "market"));
        CATEGORY_TO_GOOGLE_TYPES.put(PlaceCategory.NATURE,
                List.of("park", "national_park", "beach", "botanical_garden", "hiking_area"));
    }

    private final BandRepository bandRepository;
    private final PlaceRepository placeRepository;
    private final PlaceBookmarkRepository placeBookmarkRepository;
    private final UserRepository userRepository;
    private final GooglePlacesService googlePlacesService;
    private final ObjectMapper objectMapper;

    public PlaceSearchService(BandRepository bandRepository,
                               PlaceRepository placeRepository,
                               PlaceBookmarkRepository placeBookmarkRepository,
                               UserRepository userRepository,
                               GooglePlacesService googlePlacesService,
                               ObjectMapper objectMapper) {
        this.bandRepository = bandRepository;
        this.placeRepository = placeRepository;
        this.placeBookmarkRepository = placeBookmarkRepository;
        this.userRepository = userRepository;
        this.googlePlacesService = googlePlacesService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PlaceSearchResult> searchPlaces(Long userId, Long bandId,
                                                 PlaceCategory category,
                                                 double radiusMeters) {
        userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        Band band = bandRepository.findByIdAndIsDeletedFalse(bandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "밴드를 찾을 수 없습니다."));

        if (!band.isOverseas()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "국내 장소 검색은 카카오맵을 사용합니다.");
        }

        List<String> includedTypes = category != null
                ? CATEGORY_TO_GOOGLE_TYPES.getOrDefault(category, List.of())
                : List.of();

        NearbySearchResponse response = googlePlacesService.searchNearby(
                band.getDestinationLat(), band.getDestinationLng(),
                radiusMeters, includedTypes.isEmpty() ? null : includedTypes
        );

        if (response.places() == null || response.places().isEmpty()) {
            return List.of();
        }

        // 내 북마크 placeId 집합 (isBookmarked 표시용)
        Set<Long> myBookmarkPlaceIds = placeBookmarkRepository
                .findByBandIdAndUserIdOrderByCreatedAtDesc(bandId, userId)
                .stream()
                .map(pb -> pb.getPlace().getId())
                .collect(Collectors.toSet());

        List<PlaceSearchResult> results = new ArrayList<>();
        for (NearbySearchResponse.Place gPlace : response.places()) {
            try {
                Place place = cachePlace(gPlace);
                results.add(toSearchResult(place, myBookmarkPlaceIds));
            } catch (Exception e) {
                log.warn("장소 캐싱 실패 (externalId={}): {}", gPlace.id(), e.getMessage());
            }
        }
        return results;
    }

    private Place cachePlace(NearbySearchResponse.Place gPlace) {
        String openingHoursJson = convertOpeningHours(gPlace.regularOpeningHours());
        String thumbnailUrl = buildThumbnailUrl(gPlace);
        PlaceCategory category = mapGoogleTypes(gPlace.types());
        Float rating = gPlace.rating() != null ? gPlace.rating().floatValue() : null;
        String name = gPlace.displayName() != null ? gPlace.displayName().text() : "알 수 없는 장소";
        double lat = gPlace.location() != null ? gPlace.location().latitude() : 0;
        double lng = gPlace.location() != null ? gPlace.location().longitude() : 0;

        return placeRepository.findByApiSourceAndExternalId(PlaceApiSource.GOOGLE, gPlace.id())
                .map(existing -> {
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

    private String buildThumbnailUrl(NearbySearchResponse.Place gPlace) {
        if (gPlace.photos() == null || gPlace.photos().isEmpty()) return null;
        return googlePlacesService.buildPhotoUrl(gPlace.photos().get(0).name());
    }

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

    private PlaceCategory mapGoogleTypes(List<String> types) {
        if (types == null || types.isEmpty()) return PlaceCategory.ETC;
        for (String type : types) {
            if (FOOD_TYPES.contains(type))     return PlaceCategory.FOOD;
            if (CULTURE_TYPES.contains(type))  return PlaceCategory.CULTURE;
            if (ACTIVITY_TYPES.contains(type)) return PlaceCategory.ACTIVITY;
            if (SHOPPING_TYPES.contains(type)) return PlaceCategory.SHOPPING;
            if (NATURE_TYPES.contains(type))   return PlaceCategory.NATURE;
        }
        return PlaceCategory.ETC;
    }

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
