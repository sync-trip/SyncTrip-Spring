package com.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sync.domain.place.Place;
import com.sync.domain.place.PlaceApiSource;
import com.sync.domain.place.PlaceCategory;
import com.sync.dto.google.NearbySearchResponse;
import com.sync.dto.place.PlaceSearchResult;
import com.sync.repository.PlaceRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceLookupServiceTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private GooglePlacesService googlePlacesService;

    private PlaceLookupService placeLookupService;

    @BeforeEach
    void setUp() {
        placeLookupService = new PlaceLookupService(
                placeRepository,
                googlePlacesService,
                new ObjectMapper()
        );
    }

    // 국내 밴드도 Google Places를 사용하여 장소를 검색하고 places 테이블에 캐싱한다.
    @Test
    void searchAndCache_usesGoogleAndCachesPlace() {
        NearbySearchResponse.Place gPlace = new NearbySearchResponse.Place(
                "google-abc",
                List.of("tourist_attraction", "point_of_interest"),
                new NearbySearchResponse.LocalizedText("경복궁", "ko"),
                new NearbySearchResponse.LatLng(37.5796, 126.9770),
                4.5,
                "서울 종로구 사직로 161",
                null,
                null,
                null
        );
        NearbySearchResponse response = new NearbySearchResponse(List.of(gPlace));

        when(googlePlacesService.searchText(37.5665, 126.9780, "경복궁", null))
                .thenReturn(response);
        when(placeRepository.findByApiSourceAndExternalId(PlaceApiSource.GOOGLE, "google-abc"))
                .thenReturn(Optional.empty());
        when(placeRepository.save(any(Place.class))).thenAnswer(invocation -> {
            Place place = invocation.getArgument(0);
            setId(place, 300L);
            return place;
        });

        List<PlaceSearchResult> results = placeLookupService.searchAndCache(
                10L, 37.5665, 126.9780, "경복궁", PlaceCategory.CULTURE);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).apiSource()).isEqualTo(PlaceApiSource.GOOGLE);
        assertThat(results.get(0).externalId()).isEqualTo("google-abc");
        assertThat(results.get(0).name()).isEqualTo("경복궁");
        assertThat(results.get(0).category()).isEqualTo(PlaceCategory.CULTURE);
        assertThat(results.get(0).rating()).isEqualTo(4.5f);
        // 캐시에는 북마크를 담지 않으므로 항상 false
        assertThat(results.get(0).isBookmarked()).isFalse();
        assertThat(results.get(0).placeId()).isEqualTo(300L);
    }

    // 요청 카테고리와 다른 타입의 장소는 결과에서 제외된다.
    @Test
    void searchAndCache_filtersByCategory() {
        NearbySearchResponse.Place restaurant = new NearbySearchResponse.Place(
                "google-food",
                List.of("restaurant"),
                new NearbySearchResponse.LocalizedText("식당", "ko"),
                new NearbySearchResponse.LatLng(37.5796, 126.9770),
                4.0,
                "서울 어딘가",
                null,
                null,
                null
        );
        NearbySearchResponse response = new NearbySearchResponse(List.of(restaurant));

        when(googlePlacesService.searchText(37.5665, 126.9780, "맛집", null))
                .thenReturn(response);

        // CULTURE로 검색하면 FOOD 타입 장소는 필터링되어 결과 없음
        List<PlaceSearchResult> results = placeLookupService.searchAndCache(
                10L, 37.5665, 126.9780, "맛집", PlaceCategory.CULTURE);

        assertThat(results).isEmpty();
    }

    private void setId(Object target, Long id) {
        try {
            Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
