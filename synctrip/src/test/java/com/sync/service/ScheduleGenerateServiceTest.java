package com.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sync.domain.band.Band;
import com.sync.domain.band.BandMember;
import com.sync.domain.band.BandRole;
import com.sync.domain.band.BandStatus;
import com.sync.domain.place.Place;
import com.sync.domain.place.PlaceApiSource;
import com.sync.domain.place.PlaceBookmark;
import com.sync.domain.place.PlaceCategory;
import com.sync.domain.schedule.Schedule;
import com.sync.domain.user.User;
import com.sync.domain.vote.Vote;
import com.sync.repository.BandMemberRepository;
import com.sync.repository.BandRepository;
import com.sync.repository.PlaceBookmarkRepository;
import com.sync.repository.ScheduleAltRepository;
import com.sync.repository.ScheduleRepository;
import com.sync.repository.VoteRepository;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleGenerateServiceTest {

    @Mock private BandRepository bandRepository;
    @Mock private BandMemberRepository bandMemberRepository;
    @Mock private PlaceBookmarkRepository placeBookmarkRepository;
    @Mock private VoteRepository voteRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private ScheduleAltRepository scheduleAltRepository;

    private ScheduleGenerateService service;

    @BeforeEach
    void setUp() {
        service = new ScheduleGenerateService(
                bandRepository, bandMemberRepository, placeBookmarkRepository,
                voteRepository, scheduleRepository, scheduleAltRepository,
                new ObjectMapper()
        );
    }

    @Test
    void generate_정상_schedule저장_및_상태전환() {
        // 1명 방장, 1일 국내, CULTURE 1곳 LIKE → schedule 1개 저장, GENERATING 전환
        User owner = createUser(1L);
        Band band = createBand(10L, BandStatus.VOTING, owner,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1));
        BandMember member = createBandMember(100L, band, owner, false);
        Place place = createPlace(200L, PlaceCategory.CULTURE, 37.5665, 126.9780);
        PlaceBookmark bookmark = createBookmark(300L, band, owner, place);
        Vote vote = Vote.create(band, owner, place, 1);

        when(bandRepository.findById(10L)).thenReturn(Optional.of(band));
        when(bandMemberRepository.findByBandId(10L)).thenReturn(List.of(member));
        when(placeBookmarkRepository.findByBandId(10L)).thenReturn(List.of(bookmark));
        when(voteRepository.findByBandId(10L)).thenReturn(List.of(vote));
        when(scheduleRepository.findByBandIdOrderByDayNumberAscSlotOrderAsc(10L)).thenReturn(List.of());
        when(scheduleAltRepository.findByBandIdOrderByPriorityScoreDesc(10L)).thenReturn(List.of());
        when(scheduleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.generate(1L, 10L);

        ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleRepository, atLeastOnce()).save(captor.capture());
        Schedule saved = captor.getValue();
        assertThat(saved.getDayNumber()).isEqualTo(1);
        assertThat(saved.getPlace()).isEqualTo(place);
        assertThat(band.getStatus()).isEqualTo(BandStatus.GENERATING);
    }

    @Test
    void generate_방장아닌_멤버가_호출하면_403() {
        User owner = createUser(1L);
        Band band = createBand(10L, BandStatus.VOTING, owner,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1));

        when(bandRepository.findById(10L)).thenReturn(Optional.of(band));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.generate(2L, 10L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void generate_VOTING상태아닌_밴드는_409() {
        User owner = createUser(1L);
        Band band = createBand(10L, BandStatus.PLANNING, owner,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1));

        when(bandRepository.findById(10L)).thenReturn(Optional.of(band));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.generate(1L, 10L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void generate_장바구니_비어있으면_400() {
        User owner = createUser(1L);
        Band band = createBand(10L, BandStatus.VOTING, owner,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1));
        BandMember member = createBandMember(100L, band, owner, false);

        when(bandRepository.findById(10L)).thenReturn(Optional.of(band));
        when(bandMemberRepository.findByBandId(10L)).thenReturn(List.of(member));
        when(placeBookmarkRepository.findByBandId(10L)).thenReturn(List.of());
        when(voteRepository.findByBandId(10L)).thenReturn(List.of());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.generate(1L, 10L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void generate_같은장소를_여러명이_담아도_schedule은_중복없이_1개() {
        // 2명이 동일한 placeId=200 장소를 각자 담음
        // 중복 제거 로직이 없으면 WeightedCostFunction의 passed 리스트에 2번 들어가는 버그
        User owner = createUser(1L);
        User other = createUser(2L);
        Band band = createBand(10L, BandStatus.VOTING, owner,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1));
        BandMember m1 = createBandMember(100L, band, owner, false);
        BandMember m2 = createBandMember(101L, band, other, false);
        Place place = createPlace(200L, PlaceCategory.CULTURE, 37.5665, 126.9780);
        PlaceBookmark bm1 = createBookmark(300L, band, owner, place);
        PlaceBookmark bm2 = createBookmark(301L, band, other, place); // 같은 place
        Vote v1 = Vote.create(band, owner, place, 1);
        Vote v2 = Vote.create(band, other, place, 1);

        when(bandRepository.findById(10L)).thenReturn(Optional.of(band));
        when(bandMemberRepository.findByBandId(10L)).thenReturn(List.of(m1, m2));
        when(placeBookmarkRepository.findByBandId(10L)).thenReturn(List.of(bm1, bm2));
        when(voteRepository.findByBandId(10L)).thenReturn(List.of(v1, v2));
        when(scheduleRepository.findByBandIdOrderByDayNumberAscSlotOrderAsc(10L)).thenReturn(List.of());
        when(scheduleAltRepository.findByBandIdOrderByPriorityScoreDesc(10L)).thenReturn(List.of());
        when(scheduleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.generate(1L, 10L);

        // 동일 장소가 중복 배정되지 않고 1번만 저장
        verify(scheduleRepository, times(1)).save(any());
        assertThat(band.getStatus()).isEqualTo(BandStatus.GENERATING);
    }

    @Test
    void generate_투표후합류멤버는_AlgorithmInput에서_제외() {
        // 투표 후 합류한 멤버(m2)는 멤버 수 집계에서 빠짐 → threshold가 1로 유지
        User owner = createUser(1L);
        User lateUser = createUser(2L);
        Band band = createBand(10L, BandStatus.VOTING, owner,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1));
        BandMember m1 = createBandMember(100L, band, owner, false);
        BandMember m2 = createBandMember(101L, band, lateUser, true); // 투표 후 합류
        Place place = createPlace(200L, PlaceCategory.CULTURE, 37.5665, 126.9780);
        PlaceBookmark bm = createBookmark(300L, band, owner, place);
        Vote vote = Vote.create(band, owner, place, 1);

        when(bandRepository.findById(10L)).thenReturn(Optional.of(band));
        when(bandMemberRepository.findByBandId(10L)).thenReturn(List.of(m1, m2));
        when(placeBookmarkRepository.findByBandId(10L)).thenReturn(List.of(bm));
        when(voteRepository.findByBandId(10L)).thenReturn(List.of(vote));
        when(scheduleRepository.findByBandIdOrderByDayNumberAscSlotOrderAsc(10L)).thenReturn(List.of());
        when(scheduleAltRepository.findByBandIdOrderByPriorityScoreDesc(10L)).thenReturn(List.of());
        when(scheduleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.generate(1L, 10L);

        assertThat(band.getStatus()).isEqualTo(BandStatus.GENERATING);
    }

    @Test
    void generate_멱등성_기존_schedules_삭제후_재저장() {
        User owner = createUser(1L);
        Band band = createBand(10L, BandStatus.VOTING, owner,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1));
        BandMember member = createBandMember(100L, band, owner, false);
        Place place = createPlace(200L, PlaceCategory.CULTURE, 37.5665, 126.9780);
        PlaceBookmark bookmark = createBookmark(300L, band, owner, place);
        Vote vote = Vote.create(band, owner, place, 1);

        Schedule oldSchedule = Schedule.create(band, place, 1, 1, null, 60, null);
        when(bandRepository.findById(10L)).thenReturn(Optional.of(band));
        when(bandMemberRepository.findByBandId(10L)).thenReturn(List.of(member));
        when(placeBookmarkRepository.findByBandId(10L)).thenReturn(List.of(bookmark));
        when(voteRepository.findByBandId(10L)).thenReturn(List.of(vote));
        when(scheduleRepository.findByBandIdOrderByDayNumberAscSlotOrderAsc(10L))
                .thenReturn(List.of(oldSchedule));
        when(scheduleAltRepository.findByBandIdOrderByPriorityScoreDesc(10L)).thenReturn(List.of());
        when(scheduleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.generate(1L, 10L);

        // 기존 일정 삭제 후 새 일정 저장 확인
        verify(scheduleRepository).deleteAll(List.of(oldSchedule));
        verify(scheduleRepository, atLeastOnce()).save(any(Schedule.class));
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────────────

    private User createUser(Long id) {
        User user = User.kakaoUser("user" + id + "@test.com", "유저" + id, null, "oauth-" + id);
        setField(user, "id", id);
        return user;
    }

    private Band createBand(Long id, BandStatus status, User owner,
                            LocalDate start, LocalDate end) {
        Band band = Band.create(owner, "테스트여행", "서울",
                37.5665, 126.9780, "KR", false, start, end);
        setField(band, "id", id);
        setField(band, "status", status);
        return band;
    }

    private BandMember createBandMember(Long id, Band band, User user,
                                        boolean joinedAfterVoting) {
        BandMember member = BandMember.create(user, band, BandRole.MEMBER);
        setField(member, "id", id);
        if (joinedAfterVoting) member.markJoinedAfterVoting();
        return member;
    }

    private Place createPlace(Long id, PlaceCategory category, double lat, double lng) {
        Place place = Place.create(PlaceApiSource.KAKAO, "kakao-" + id,
                "장소" + id, category, lat, lng, "테스트주소", 4.5f, null, null, null);
        setField(place, "id", id);
        return place;
    }

    private PlaceBookmark createBookmark(Long id, Band band, User user, Place place) {
        PlaceBookmark bm = PlaceBookmark.create(band, user, place);
        setField(bm, "id", id);
        return bm;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
