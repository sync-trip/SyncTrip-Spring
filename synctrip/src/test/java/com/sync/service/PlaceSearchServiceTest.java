package com.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sync.domain.band.Band;
import com.sync.domain.band.BandMember;
import com.sync.domain.band.BandRole;
import com.sync.domain.place.Place;
import com.sync.domain.place.PlaceApiSource;
import com.sync.domain.place.PlaceCategory;
import com.sync.domain.user.User;
import com.sync.dto.google.NearbySearchResponse;
import com.sync.dto.place.PlaceSearchResult;
import com.sync.repository.BandRepository;
import com.sync.repository.PlaceBookmarkRepository;
import com.sync.repository.PlaceRepository;
import com.sync.repository.UserRepository;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceSearchServiceTest {

    @Mock
    private BandRepository bandRepository;

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private PlaceBookmarkRepository placeBookmarkRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GooglePlacesService googlePlacesService;

    private PlaceSearchService placeSearchService;

    @BeforeEach
    void setUp() {
        placeSearchService = new PlaceSearchService(
                bandRepository,
                placeRepository,
                placeBookmarkRepository,
                userRepository,
                googlePlacesService,
                new ObjectMapper()
        );
    }

    // 국내 밴드도 Google Places를 사용하여 장소를 검색하고 캐싱한다.
    @Test
    void searchPlaces_domesticUsesGoogleAndCachesPlace() {
        User user = createUser(1L);
        Band band = createBand(10L); // is_overseas = false

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

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandRepository.findByIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(band));
        when(placeBookmarkRepository.findByBandIdAndUserIdOrderByCreatedAtDesc(10L, 1L)).thenReturn(List.of());
        when(googlePlacesService.searchText(37.5665, 126.9780, "경복궁"))
                .thenReturn(response);
        when(placeRepository.findByApiSourceAndExternalId(PlaceApiSource.GOOGLE, "google-abc"))
                .thenReturn(Optional.empty());
        when(placeRepository.save(any(Place.class))).thenAnswer(invocation -> {
            Place place = invocation.getArgument(0);
            setId(place, 300L);
            return place;
        });

        List<PlaceSearchResult> results = placeSearchService.searchPlaces(1L, 10L, "경복궁", PlaceCategory.CULTURE);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).apiSource()).isEqualTo(PlaceApiSource.GOOGLE);
        assertThat(results.get(0).externalId()).isEqualTo("google-abc");
        assertThat(results.get(0).name()).isEqualTo("경복궁");
        assertThat(results.get(0).category()).isEqualTo(PlaceCategory.CULTURE);
        assertThat(results.get(0).rating()).isEqualTo(4.5f);
        assertThat(results.get(0).isBookmarked()).isFalse();
        assertThat(results.get(0).placeId()).isEqualTo(300L);
    }

    @Test
    void searchPlaces_rejectsMissingUser() {
        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> placeSearchService.searchPlaces(1L, 10L, null, null));

        assertThat(ex.getStatusCode().value()).isEqualTo(404);
    }

    // 국내 밴드도 keyword 없으면 BAD_REQUEST
    @Test
    void searchPlaces_domesticRequiresKeyword() {
        User user = createUser(1L);
        Band band = createBand(10L); // is_overseas = false

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandRepository.findByIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(band));
        when(placeBookmarkRepository.findByBandIdAndUserIdOrderByCreatedAtDesc(10L, 1L)).thenReturn(List.of());

        ResponseStatusException ex = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> placeSearchService.searchPlaces(1L, 10L, "", PlaceCategory.CULTURE)
        );

        assertThat(ex.getStatusCode().value()).isEqualTo(400);
    }

    // 해외 밴드도 동일하게 keyword 없으면 BAD_REQUEST
    @Test
    void searchPlaces_overseasRequiresKeyword() {
        User user = createUser(1L);
        Band band = createBandOverseas(20L, 35.6762, 139.6503);

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandRepository.findByIdAndIsDeletedFalse(20L)).thenReturn(Optional.of(band));
        when(placeBookmarkRepository.findByBandIdAndUserIdOrderByCreatedAtDesc(20L, 1L)).thenReturn(List.of());

        ResponseStatusException ex = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> placeSearchService.searchPlaces(1L, 20L, "", PlaceCategory.CULTURE)
        );

        assertThat(ex.getStatusCode().value()).isEqualTo(400);
    }

    private User createUser(Long id) {
        User user = User.kakaoUser("user@example.com", "사용자", null, "oauth-1");
        setId(user, id);
        return user;
    }

    private Band createBand(Long id) {
        User owner = createUser(999L);
        Band band = Band.create(
                owner,
                "봄여행",
                "서울",
                37.5665,
                126.9780,
                "KR",
                false,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 5),
                com.sync.domain.band.TravelStyle.PACKED, null, null, null, null
        );
        setId(band, id);
        return band;
    }

    private Band createBandOverseas(Long id, double lat, double lng) {
        User owner = createUser(999L);
        Band band = Band.create(
                owner,
                "해외여행",
                "도쿄",
                lat,
                lng,
                "JP",
                true,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 5),
                com.sync.domain.band.TravelStyle.PACKED, null, null, null, null
        );
        setId(band, id);
        return band;
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
