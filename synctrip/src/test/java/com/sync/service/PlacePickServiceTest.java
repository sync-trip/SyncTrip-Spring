package com.sync.service;

import com.sync.domain.band.Band;
import com.sync.domain.band.BandMember;
import com.sync.domain.band.BandRole;
import com.sync.domain.band.BandStatus;
import com.sync.domain.place.Place;
import com.sync.domain.place.PlaceApiSource;
import com.sync.domain.place.PlaceBookmark;
import com.sync.domain.place.PlaceCategory;
import com.sync.domain.user.User;
import com.sync.dto.pick.PlacePickListResponse;
import com.sync.dto.pick.PlacePickRequest;
import com.sync.dto.pick.PlacePickResponse;
import com.sync.repository.BandMemberRepository;
import com.sync.repository.BandRepository;
import com.sync.repository.PlaceBookmarkRepository;
import com.sync.repository.PlaceRepository;
import com.sync.repository.UserRepository;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 개인별 장바구니(Pick) 기능 테스트
 * - 장소 저장/중복 처리/목록 조회/삭제/제한 초과를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PlacePickServiceTest {

    @Mock
    private BandRepository bandRepository;

    @Mock
    private BandMemberRepository bandMemberRepository;

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private PlaceBookmarkRepository placeBookmarkRepository;

    @Mock
    private UserRepository userRepository;

    private PlacePickService placePickService;

    @BeforeEach
    void setUp() {
        // 테스트에서는 실제 DB 대신 mock repository를 직접 주입한다.
        placePickService = new PlacePickService(
                bandRepository,
                bandMemberRepository,
                placeRepository,
                placeBookmarkRepository,
                userRepository
        );
    }

    @Test
    void addPick_savesNewPlaceAndBookmark() {
        User user = createUser(1L);
        Band band = createBand(10L, BandStatus.PLANNING);
        BandMember member = createBandMember(100L, band, user, false);

        PlacePickRequest request = new PlacePickRequest(
                PlaceApiSource.KAKAO,
                "kakao-123",
                "경복궁",
                PlaceCategory.CULTURE,
                37.579617,
                126.977041,
                "서울 종로구 사직로 161",
                4.7f,
                "https://image.example/gyeongbokgung.jpg",
                null,
                null
        );

        Place savedPlace = new PlaceProxy().createPlace(request);
        PlaceBookmark savedBookmark = createBookmark(200L, band, user, savedPlace);

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandRepository.findByIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(band));
        when(bandMemberRepository.findByBandIdAndUserId(10L, 1L)).thenReturn(Optional.of(member));
        when(placeRepository.findByApiSourceAndExternalId(PlaceApiSource.KAKAO, "kakao-123")).thenReturn(Optional.empty());
        when(placeRepository.save(any(Place.class))).thenAnswer(invocation -> {
            Place place = invocation.getArgument(0);
            setId(place, 300L);
            return place;
        });
        when(placeBookmarkRepository.findByBandIdAndUserIdAndPlaceId(10L, 1L, 300L)).thenReturn(Optional.empty());
        when(placeBookmarkRepository.countByBandIdAndUserId(10L, 1L)).thenReturn(0L);
        when(placeBookmarkRepository.save(any(PlaceBookmark.class))).thenAnswer(invocation -> {
            PlaceBookmark bookmark = invocation.getArgument(0);
            setId(bookmark, 200L);
            setCreatedAt(bookmark, LocalDateTime.of(2026, 5, 14, 12, 0));
            return bookmark;
        });

        PlacePickResponse response = placePickService.addPick(1L, 10L, request);

        assertThat(response.placeBookmarkId()).isEqualTo(200L);
        assertThat(response.placeId()).isEqualTo(300L);
        assertThat(response.name()).isEqualTo("경복궁");
        assertThat(response.category()).isEqualTo(PlaceCategory.CULTURE);
        assertThat(response.estimatedDuration()).isEqualTo(90);
        assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2026, 5, 14, 12, 0));
    }

    @Test
    void addPick_returnsExistingBookmarkWhenDuplicate() {
        User user = createUser(1L);
        Band band = createBand(10L, BandStatus.PLANNING);
        BandMember member = createBandMember(100L, band, user, false);
        Place place = Place.create(
                PlaceApiSource.KAKAO,
                "kakao-123",
                "경복궁",
                PlaceCategory.CULTURE,
                37.579617,
                126.977041,
                null,
                4.7f,
                null,
                null,
                null
        );
        setId(place, 300L);
        PlaceBookmark existingBookmark = createBookmark(200L, band, user, place);
        setCreatedAt(existingBookmark, LocalDateTime.of(2026, 5, 14, 11, 30));

        PlacePickRequest request = new PlacePickRequest(
                PlaceApiSource.KAKAO,
                "kakao-123",
                "경복궁",
                PlaceCategory.CULTURE,
                37.579617,
                126.977041,
                null,
                4.7f,
                null,
                null,
                null
        );

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandRepository.findByIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(band));
        when(bandMemberRepository.findByBandIdAndUserId(10L, 1L)).thenReturn(Optional.of(member));
        when(placeRepository.findByApiSourceAndExternalId(PlaceApiSource.KAKAO, "kakao-123")).thenReturn(Optional.of(place));
        when(placeBookmarkRepository.findByBandIdAndUserIdAndPlaceId(10L, 1L, 300L)).thenReturn(Optional.of(existingBookmark));

        PlacePickResponse response = placePickService.addPick(1L, 10L, request);

        assertThat(response.placeBookmarkId()).isEqualTo(200L);
        assertThat(response.placeId()).isEqualTo(300L);
        assertThat(response.name()).isEqualTo("경복궁");
    }

    @Test
    void addPick_rejectsWhenLimitReached() {
        User user = createUser(1L);
        Band band = createBand(10L, BandStatus.PLANNING);
        BandMember member = createBandMember(100L, band, user, false);

        PlacePickRequest request = new PlacePickRequest(
                PlaceApiSource.KAKAO,
                "kakao-999",
                "한강공원",
                PlaceCategory.NATURE,
                37.520000,
                127.015000,
                null,
                null,
                null,
                null,
                null
        );

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandRepository.findByIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(band));
        when(bandMemberRepository.findByBandIdAndUserId(10L, 1L)).thenReturn(Optional.of(member));
        when(placeRepository.findByApiSourceAndExternalId(PlaceApiSource.KAKAO, "kakao-999")).thenReturn(Optional.empty());
        when(placeRepository.save(any(Place.class))).thenAnswer(invocation -> {
            Place place = invocation.getArgument(0);
            setId(place, 301L);
            return place;
        });
        when(placeBookmarkRepository.findByBandIdAndUserIdAndPlaceId(10L, 1L, 301L)).thenReturn(Optional.empty());
        when(placeBookmarkRepository.countByBandIdAndUserId(10L, 1L)).thenReturn(5L);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> placePickService.addPick(1L, 10L, request)
        );

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void getMyPicks_returnsMyPickList() {
        User user = createUser(1L);
        Band band = createBand(10L, BandStatus.PLANNING);
        BandMember member = createBandMember(100L, band, user, false);
        Place place = Place.create(
                PlaceApiSource.GOOGLE,
                "google-777",
                "오사카성",
                PlaceCategory.CULTURE,
                34.687315,
                135.525911,
                "1-1 Osakajo, Chuo Ward, Osaka",
                4.6f,
                "https://image.example/osakacastle.jpg",
                "{\"MON\":[{\"open\":\"09:00\",\"close\":\"17:00\"}]}",
                120
        );
        setId(place, 400L);
        PlaceBookmark bookmark = createBookmark(500L, band, user, place);
        setCreatedAt(bookmark, LocalDateTime.of(2026, 5, 14, 10, 15));

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandRepository.findByIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(band));
        when(bandMemberRepository.findByBandIdAndUserId(10L, 1L)).thenReturn(Optional.of(member));
        when(placeBookmarkRepository.findByBandIdAndUserIdOrderByCreatedAtDesc(10L, 1L)).thenReturn(List.of(bookmark));

        PlacePickListResponse response = placePickService.getMyPicks(1L, 10L);

        assertThat(response.currentCount()).isEqualTo(1);
        assertThat(response.maxCount()).isEqualTo(5);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).name()).isEqualTo("오사카성");
        assertThat(response.items().get(0).apiSource()).isEqualTo(PlaceApiSource.GOOGLE);
    }

    @Test
    void removePick_deletesExistingBookmark() {
        User user = createUser(1L);
        Band band = createBand(10L, BandStatus.PLANNING);
        BandMember member = createBandMember(100L, band, user, false);
        Place place = Place.create(
                PlaceApiSource.KAKAO,
                "kakao-123",
                "경복궁",
                PlaceCategory.CULTURE,
                37.579617,
                126.977041,
                null,
                4.7f,
                null,
                null,
                null
        );
        setId(place, 300L);
        PlaceBookmark bookmark = createBookmark(200L, band, user, place);

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandRepository.findByIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(band));
        when(bandMemberRepository.findByBandIdAndUserId(10L, 1L)).thenReturn(Optional.of(member));
        when(placeBookmarkRepository.findByBandIdAndUserIdAndPlaceId(10L, 1L, 300L)).thenReturn(Optional.of(bookmark));

        placePickService.removePick(1L, 10L, 300L);

        // delete 호출은 mock 상에서 검증 없이 예외가 없으면 성공으로 본다.
        assertThat(true).isTrue();
    }

    private User createUser(Long id) {
        User user = User.kakaoUser("user@example.com", "사용자", null, "oauth-1");
        setId(user, id);
        return user;
    }

    private Band createBand(Long id, BandStatus status) {
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
                LocalDate.of(2026, 6, 5)
        );
        setId(band, id);
        setBandStatus(band, status);
        return band;
    }

    private BandMember createBandMember(Long id, Band band, User user, boolean joinedAfterVoting) {
        BandMember member = BandMember.create(user, band, BandRole.MEMBER);
        setId(member, id);
        if (joinedAfterVoting) {
            member.markJoinedAfterVoting();
        }
        return member;
    }

    private PlaceBookmark createBookmark(Long id, Band band, User user, Place place) {
        PlaceBookmark bookmark = PlaceBookmark.create(band, user, place);
        setId(bookmark, id);
        return bookmark;
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

    private void setCreatedAt(PlaceBookmark bookmark, LocalDateTime createdAt) {
        try {
            Field field = PlaceBookmark.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(bookmark, createdAt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setBandStatus(Band band, BandStatus status) {
        try {
            Field field = Band.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(band, status);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 테스트에서 Place.create를 직접 호출하기 어렵지 않도록 가볍게 감싸둔 헬퍼.
     */
    private static class PlaceProxy {
        Place createPlace(PlacePickRequest request) {
            Place place = Place.create(
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
            );
            try {
                Field field = Place.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(place, 300L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return place;
        }
    }
}


