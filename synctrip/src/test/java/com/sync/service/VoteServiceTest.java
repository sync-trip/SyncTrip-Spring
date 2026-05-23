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
import com.sync.domain.vote.Vote;
import com.sync.dto.vote.VotePlaceResponse;
import com.sync.dto.vote.VoteRequest;
import com.sync.dto.vote.VoteResponse;
import com.sync.dto.vote.VoteStatusResponse;
import com.sync.dto.ws.VoteEvent;
import com.sync.repository.BandMemberRepository;
import com.sync.repository.BandRepository;
import com.sync.repository.PlaceBookmarkRepository;
import com.sync.repository.PlaceRepository;
import com.sync.repository.UserRepository;
import com.sync.repository.VoteRepository;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoteServiceTest {

    @Mock
    private BandRepository bandRepository;
    @Mock
    private BandMemberRepository bandMemberRepository;
    @Mock
    private PlaceRepository placeRepository;
    @Mock
    private PlaceBookmarkRepository placeBookmarkRepository;
    @Mock
    private VoteRepository voteRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private BandService bandService;

    private VoteService voteService;

    @BeforeEach
    void setUp() {
        voteService = new VoteService(
                bandRepository,
                bandMemberRepository,
                placeRepository,
                placeBookmarkRepository,
                voteRepository,
                userRepository,
                messagingTemplate,
                bandService
        );
    }

    @Test
    void getVotablePlaces_marksMyBookmark() {
        User user = createUser(1L);
        Band band = createBand(10L, BandStatus.VOTING);
        Place place = createPlace(300L, PlaceApiSource.GOOGLE, "google-1", "Eiffel Tower", PlaceCategory.CULTURE);
        PlaceBookmark bookmark = PlaceBookmark.create(band, user, place);
        setId(bookmark, 700L);

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandRepository.findByIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(band));
        when(placeRepository.findAllByBandId(10L)).thenReturn(List.of(place));
        when(placeBookmarkRepository.findByBandIdAndUserIdOrderByCreatedAtDesc(10L, 1L)).thenReturn(List.of(bookmark));

        List<VotePlaceResponse> result = voteService.getVotablePlaces(1L, 10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).placeId()).isEqualTo(300L);
        assertThat(result.get(0).myBookmark()).isTrue();
    }

    @Test
    void castVote_changesOwnBookmarkResultToAutoLikeAndSendsEvent() {
        User user = createUser(1L);
        Band band = createBand(10L, BandStatus.VOTING);
        BandMember member = createBandMember(100L, band, user, false);
        Place place = createPlace(300L, PlaceApiSource.KAKAO, "kakao-123", "Gyeongbokgung", PlaceCategory.CULTURE);

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandRepository.findByIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(band));
        when(bandMemberRepository.findByBandIdAndUserId(10L, 1L)).thenReturn(Optional.of(member));
        when(placeRepository.findById(300L)).thenReturn(Optional.of(place));
        when(voteRepository.existsByBandIdAndUserIdAndPlaceId(10L, 1L, 300L)).thenReturn(false);
        when(placeBookmarkRepository.existsByBandIdAndUserIdAndPlaceId(10L, 1L, 300L)).thenReturn(true);
        when(voteRepository.save(any(Vote.class))).thenAnswer(invocation -> {
            Vote vote = invocation.getArgument(0);
            setId(vote, 900L);
            setVotedAt(vote, LocalDateTime.of(2026, 5, 20, 23, 50));
            return vote;
        });
        when(placeRepository.findAllByBandId(10L)).thenReturn(List.of(place));
        when(voteRepository.countByBandIdAndUserId(10L, 1L)).thenReturn(1L);

        VoteResponse response = voteService.castVote(1L, 10L, new VoteRequest(300L, -1));

        assertThat(response.voteId()).isEqualTo(900L);
        assertThat(response.placeId()).isEqualTo(300L);
        assertThat(response.result()).isEqualTo(0);
        assertThat(response.votedAt()).isEqualTo(LocalDateTime.of(2026, 5, 20, 23, 50));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/bands/10/votes"), payloadCaptor.capture());
        VoteEvent event = (VoteEvent) payloadCaptor.getValue();
        assertThat(event.userId()).isEqualTo(1L);
        assertThat(event.placeId()).isEqualTo(300L);
        assertThat(event.myVotedCount()).isEqualTo(1);
        assertThat(event.totalPlaces()).isEqualTo(1);
    }

    @Test
    void castVote_rejectsDuplicateVote() {
        User user = createUser(1L);
        Band band = createBand(10L, BandStatus.VOTING);
        BandMember member = createBandMember(100L, band, user, false);
        Place place = createPlace(300L, PlaceApiSource.KAKAO, "kakao-123", "Gyeongbokgung", PlaceCategory.CULTURE);

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandRepository.findByIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(band));
        when(bandMemberRepository.findByBandIdAndUserId(10L, 1L)).thenReturn(Optional.of(member));
        when(placeRepository.findById(300L)).thenReturn(Optional.of(place));
        when(voteRepository.existsByBandIdAndUserIdAndPlaceId(10L, 1L, 300L)).thenReturn(true);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> voteService.castVote(1L, 10L, new VoteRequest(300L, 1))
        );

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void castVote_rejectsInvalidResult() {
        User user = createUser(1L);
        Band band = createBand(10L, BandStatus.VOTING);
        BandMember member = createBandMember(100L, band, user, false);

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandRepository.findByIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(band));
        when(bandMemberRepository.findByBandIdAndUserId(10L, 1L)).thenReturn(Optional.of(member));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> voteService.castVote(1L, 10L, new VoteRequest(300L, 0))
        );

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getVoteStatus_returnsMyProgress() {
        User user = createUser(1L);
        Band band = createBand(10L, BandStatus.VOTING);
        Place p1 = createPlace(300L, PlaceApiSource.GOOGLE, "g-1", "A", PlaceCategory.CULTURE);
        Place p2 = createPlace(301L, PlaceApiSource.GOOGLE, "g-2", "B", PlaceCategory.FOOD);

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandRepository.findByIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(band));
        when(placeRepository.findAllByBandId(10L)).thenReturn(List.of(p1, p2));
        when(voteRepository.countByBandIdAndUserId(10L, 1L)).thenReturn(1L);

        VoteStatusResponse response = voteService.getVoteStatus(1L, 10L);

        assertThat(response.totalPlaces()).isEqualTo(2);
        assertThat(response.myVotedCount()).isEqualTo(1);
        assertThat(response.myComplete()).isFalse();
    }

    private User createUser(Long id) {
        User user = User.kakaoUser("user@example.com", "tester", null, "oauth-1");
        setId(user, id);
        return user;
    }

    private Band createBand(Long id, BandStatus status) {
        User owner = createUser(999L);
        Band band = Band.create(
                owner,
                "trip",
                "seoul",
                37.5665,
                126.9780,
                "KR",
                false,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 5),
                com.sync.domain.band.TravelStyle.PACKED,
                null,
                null,
                null
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

    private Place createPlace(Long id, PlaceApiSource source, String externalId, String name, PlaceCategory category) {
        Place place = Place.create(source, externalId, name, category, 37.0, 127.0, "addr", 4.5f, null, null, null);
        setId(place, id);
        return place;
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

    private void setVotedAt(Vote vote, LocalDateTime votedAt) {
        try {
            Field field = Vote.class.getDeclaredField("votedAt");
            field.setAccessible(true);
            field.set(vote, votedAt);
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
}

