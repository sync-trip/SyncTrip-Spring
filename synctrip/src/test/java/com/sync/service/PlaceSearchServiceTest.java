package com.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sync.domain.band.Band;
import com.sync.domain.band.BandMember;
import com.sync.domain.band.BandRole;
import com.sync.domain.place.Place;
import com.sync.domain.place.PlaceApiSource;
import com.sync.domain.place.PlaceCategory;
import com.sync.domain.user.User;
import com.sync.dto.kakao.KakaoLocalSearchResponse;
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
import static org.mockito.Mockito.verifyNoInteractions;
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
    private KakaoPlacesService kakaoPlacesService;

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
                kakaoPlacesService,
                googlePlacesService,
                new ObjectMapper()
        );
    }

    @Test
    void searchPlaces_routesDomesticSearchToKakaoAndCachesPlace() {
        User user = createUser(1L);
        Band band = createBand(10L);
        BandMember member = BandMember.create(user, band, BandRole.MEMBER);
        setId(member, 100L);
        KakaoLocalSearchResponse.Document doc = new KakaoLocalSearchResponse.Document(
                "kakao-123",
                "경복궁",
                "관광명소",
                "AT4",
                "관광명소",
                "서울 종로구 사직로 161",
                "서울 종로구 사직로 161",
                "126.977041",
                "37.579617",
                "02-123-4567",
                "https://place.map.kakao.com/123",
                "0"
        );

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandRepository.findByIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(band));
        when(placeBookmarkRepository.findByBandIdAndUserIdOrderByCreatedAtDesc(10L, 1L)).thenReturn(List.of());
        when(kakaoPlacesService.searchNearby(37.5665, 126.9780, 4200.0, PlaceCategory.CULTURE))
                .thenReturn(List.of(doc));
        when(placeRepository.findByApiSourceAndExternalId(PlaceApiSource.KAKAO, "kakao-123"))
                .thenReturn(Optional.empty());
        when(placeRepository.save(any(Place.class))).thenAnswer(invocation -> {
            Place place = invocation.getArgument(0);
            setId(place, 300L);
            return place;
        });

        List<PlaceSearchResult> results = placeSearchService.searchPlaces(1L, 10L, PlaceCategory.CULTURE, 4200);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).apiSource()).isEqualTo(PlaceApiSource.KAKAO);
        assertThat(results.get(0).externalId()).isEqualTo("kakao-123");
        assertThat(results.get(0).name()).isEqualTo("경복궁");
        assertThat(results.get(0).category()).isEqualTo(PlaceCategory.CULTURE);
        assertThat(results.get(0).isBookmarked()).isFalse();
        assertThat(results.get(0).placeId()).isEqualTo(300L);

        verifyNoInteractions(googlePlacesService);
    }

    @Test
    void searchPlaces_rejectsMissingUser() {
        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.empty());

        org.springframework.web.server.ResponseStatusException ex =
                org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,
                        () -> placeSearchService.searchPlaces(1L, 10L, null, 5000));

        assertThat(ex.getStatusCode().value()).isEqualTo(404);
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
                com.sync.domain.band.TravelStyle.PACKED, null, null, null
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


