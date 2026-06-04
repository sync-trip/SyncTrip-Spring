package com.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sync.domain.band.Band;
import com.sync.domain.band.BandMember;
import com.sync.domain.band.BandRole;
import com.sync.domain.band.BandStatus;
import com.sync.domain.place.Place;
import com.sync.domain.place.PlaceApiSource;
import com.sync.domain.place.PlaceCategory;
import com.sync.domain.schedule.Schedule;
import com.sync.domain.schedule.ScheduleAlt;
import com.sync.domain.user.User;
import com.sync.dto.schedule.PlanBResponse;
import com.sync.dto.schedule.ScheduleResponse;
import com.sync.repository.BandMemberRepository;
import com.sync.repository.BandRepository;
import com.sync.repository.PlaceBookmarkRepository;
import com.sync.repository.PlaceRepository;
import com.sync.repository.ScheduleAltRepository;
import com.sync.repository.ScheduleRepository;
import com.sync.repository.UserRepository;
import com.sync.repository.VoteRepository;
import com.sync.service.GoogleDistanceService;
import com.sync.service.HolidayService;
import com.sync.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private ScheduleAltRepository scheduleAltRepository;
    @Mock
    private BandRepository bandRepository;
    @Mock
    private BandMemberRepository bandMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PlaceRepository placeRepository;
    @Mock
    private PlaceBookmarkRepository placeBookmarkRepository;
    @Mock
    private VoteRepository voteRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private NotificationService notificationService;

    private ScheduleService scheduleService;

    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleService(
                scheduleRepository,
                scheduleAltRepository,
                bandRepository,
                bandMemberRepository,
                userRepository,
                placeRepository,
                placeBookmarkRepository,
                voteRepository,
                new ObjectMapper(),
                messagingTemplate,
                notificationService,
                mock(HolidayService.class),
                mock(GoogleDistanceService.class)
        );
    }

    @Test
    void getPlanBRecommendations_overseasFiltersClosedAndLowPriorityCandidates() {
        User user = createUser(1L);
        Band band = createBand(10L, true);
        BandMember member = BandMember.create(user, band, BandRole.MEMBER);
        setId(member, 100L);

        Place target = createPlace(
                1000L,
                "target",
                "Target Museum",
                PlaceCategory.CULTURE,
                35.6800,
                139.7600,
                "{\"MON\":[{\"open\":\"09:00\",\"close\":\"20:00\"}]}"
        );
        Place closedAtVisit = createPlace(
                1001L,
                "closed",
                "Closed Candidate",
                PlaceCategory.CULTURE,
                35.6810,
                139.7610,
                "{\"MON\":[{\"open\":\"09:00\",\"close\":\"17:00\"}]}"
        );
        Place lowPriority = createPlace(
                1002L,
                "low",
                "Low Priority Candidate",
                PlaceCategory.CULTURE,
                35.6812,
                139.7612,
                "{\"MON\":[{\"open\":\"09:00\",\"close\":\"23:00\"}]}"
        );
        Place valid = createPlace(
                1003L,
                "valid",
                "Valid Candidate",
                PlaceCategory.CULTURE,
                35.6808,
                139.7608,
                "{\"MON\":[{\"open\":\"09:00\",\"close\":\"23:00\"}]}"
        );

        Schedule targetSchedule = Schedule.create(band, target, 1, 1, LocalTime.of(18, 0), 90, null, false, false, false, false, false);
        setId(targetSchedule, 500L);

        ScheduleAlt altClosed = ScheduleAlt.create(band, closedAtVisit, 0.95f);
        setId(altClosed, 700L);
        ScheduleAlt altLowPriority = ScheduleAlt.create(band, lowPriority, 0.20f);
        setId(altLowPriority, 701L);
        ScheduleAlt altValid = ScheduleAlt.create(band, valid, 0.85f);
        setId(altValid, 702L);

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandRepository.findByIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(band));
        when(bandMemberRepository.findByBandIdAndUserId(10L, 1L)).thenReturn(Optional.of(member));
        when(placeRepository.findById(1000L)).thenReturn(Optional.of(target));
        when(scheduleRepository.findByBandIdOrderByDayNumberAscSlotOrderAsc(10L)).thenReturn(List.of(targetSchedule));
        when(scheduleAltRepository.findByBandIdOrderByPriorityScoreDesc(10L))
                .thenReturn(List.of(altClosed, altLowPriority, altValid));

        List<PlanBResponse> result = scheduleService.getPlanBRecommendations(1L, 10L, 1000L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).placeId()).isEqualTo(1003L);
        assertThat(result.get(0).placeInfo().name()).isEqualTo("Valid Candidate");
        assertThat(result.get(0).searchRadiusKmUsed()).isEqualTo(1.0);
        assertThat(result.get(0).fallbackLevel()).isEqualTo(0);
    }

    @Test
    void getPlanBRecommendations_expandsRadiusToStageThreeWhenNeeded() {
        User user = createUser(1L);
        Band band = createBand(20L, false);
        BandMember member = BandMember.create(user, band, BandRole.MEMBER);
        setId(member, 200L);

        Place target = createPlace(
                2000L,
                "target-2",
                "Target Place",
                PlaceCategory.CULTURE,
                37.5665,
                126.9780,
                null
        );
        // 약 2.2~2.4km 거리 후보 (1,2km 단계에서는 제외, 3km 단계에서만 포함)
        Place stageThreeCandidate = createPlace(
                2001L,
                "stage3",
                "Stage3 Candidate",
                PlaceCategory.CULTURE,
                37.5865,
                126.9780,
                null
        );

        Schedule targetSchedule = Schedule.create(band, target, 1, 1, LocalTime.of(12, 0), 90, null, false, false, false, false, false);
        setId(targetSchedule, 800L);

        ScheduleAlt alt = ScheduleAlt.create(band, stageThreeCandidate, 0.9f);
        setId(alt, 900L);

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandRepository.findByIdAndIsDeletedFalse(20L)).thenReturn(Optional.of(band));
        when(bandMemberRepository.findByBandIdAndUserId(20L, 1L)).thenReturn(Optional.of(member));
        when(placeRepository.findById(2000L)).thenReturn(Optional.of(target));
        when(scheduleRepository.findByBandIdOrderByDayNumberAscSlotOrderAsc(20L)).thenReturn(List.of(targetSchedule));
        when(scheduleAltRepository.findByBandIdOrderByPriorityScoreDesc(20L)).thenReturn(List.of(alt));

        List<PlanBResponse> result = scheduleService.getPlanBRecommendations(1L, 20L, 2000L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).placeId()).isEqualTo(2001L);
        assertThat(result.get(0).searchRadiusKmUsed()).isEqualTo(3.0);
        assertThat(result.get(0).fallbackLevel()).isEqualTo(2);
    }

    @Test
    void getPlanBRecommendations_returnsUpToSevenCandidates() {
        /* 최대 7개 후보까지 반환되는지 검증 (인수인계 문서 Line 136) */
        User user = createUser(1L);
        Band band = createBand(30L, false);
        BandMember member = BandMember.create(user, band, BandRole.MEMBER);
        setId(member, 300L);

        Place target = createPlace(3000L, "target-3", "Target", PlaceCategory.FOOD, 37.5665, 126.9780, null);

        Schedule targetSchedule = Schedule.create(band, target, 1, 1, LocalTime.of(12, 0), 90, null, false, false, false, false, false);
        setId(targetSchedule, 1000L);

        /* 10개의 후보 생성 (모두 1km 이내) */
        List<ScheduleAlt> alts = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            // 거리: 약 0.5-0.95km (1km 단계 내)
            double offsetLat = 37.5665 + (i * 0.0005);
            Place candidate = createPlace(
                    (long)(3001 + i),
                    "cand-" + i,
                    "Candidate " + i,
                    PlaceCategory.FOOD,
                    offsetLat,
                    126.9780,
                    null
            );
            ScheduleAlt alt = ScheduleAlt.create(band, candidate, 0.9f - (i * 0.01f));
            setId(alt, (long)(2000 + i));
            alts.add(alt);
        }

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandRepository.findByIdAndIsDeletedFalse(30L)).thenReturn(Optional.of(band));
        when(bandMemberRepository.findByBandIdAndUserId(30L, 1L)).thenReturn(Optional.of(member));
        when(placeRepository.findById(3000L)).thenReturn(Optional.of(target));
        when(scheduleRepository.findByBandIdOrderByDayNumberAscSlotOrderAsc(30L)).thenReturn(List.of(targetSchedule));
        when(scheduleAltRepository.findByBandIdOrderByPriorityScoreDesc(30L)).thenReturn(alts);

        List<PlanBResponse> result = scheduleService.getPlanBRecommendations(1L, 30L, 3000L);

        /* 최대 7개까지만 반환 (더 있어도) */
        assertThat(result).hasSize(7);
        /* 모두 stage 0 (1km 단계)에서 나옴 */
        assertThat(result).allSatisfy(r -> assertThat(r.fallbackLevel()).isEqualTo(0));
        /* 점수 내림차순 정렬 확인 */
        for (int i = 0; i < result.size() - 1; i++) {
            assertThat(result.get(i).recommendScore()).isGreaterThanOrEqualTo(result.get(i + 1).recommendScore());
        }
    }

    private User createUser(Long id) {
        User user = User.kakaoUser("user@example.com", "tester", null, "oauth-1");
        setId(user, id);
        return user;
    }

    private Band createBand(Long id, boolean overseas) {
        User owner = createUser(999L);
        Band band = Band.create(
                owner,
                "trip",
                overseas ? "tokyo" : "seoul",
                35.6804,
                139.7690,
                overseas ? "JP" : "KR",
                overseas,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 3),
                com.sync.domain.band.TravelStyle.PACKED,
                null,
                null,
                null,
                null
        );
        setId(band, id);
        return band;
    }

    private Place createPlace(Long id,
                              String externalId,
                              String name,
                              PlaceCategory category,
                              double lat,
                              double lng,
                              String openingHoursJson) {
        Place place = Place.create(
                PlaceApiSource.GOOGLE,
                externalId,
                name,
                category,
                lat,
                lng,
                "address",
                4.5f,
                null,
                openingHoursJson,
                null
        );
        setId(place, id);
        return place;
    }

    // ── DONE 편집 차단 (항목 3) ──────────────────────────────────────────────

    @Test
    void startEditing_DONE_상태면_409() {
        User user = createUser(1L);
        Band band = createBand(40L, false);
        setField(band, "status", BandStatus.DONE);
        BandMember member = BandMember.create(user, band, BandRole.MEMBER);
        setId(member, 400L);

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandRepository.findByIdAndIsDeletedFalse(40L)).thenReturn(Optional.of(band));
        when(bandMemberRepository.findByBandIdAndUserId(40L, 1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> scheduleService.startEditing(1L, 40L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    // ── joined_after_voting 편집 금지 (항목 4) ───────────────────────────────

    @Test
    void startEditing_joined_after_voting_멤버면_403() {
        User user = createUser(1L);
        Band band = createBand(41L, false);
        BandMember member = BandMember.create(user, band, BandRole.MEMBER);
        setId(member, 410L);
        setField(member, "joinedAfterVoting", true);

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandRepository.findByIdAndIsDeletedFalse(41L)).thenReturn(Optional.of(band));
        when(bandMemberRepository.findByBandIdAndUserId(41L, 1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> scheduleService.startEditing(1L, 41L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ── getSchedule canEdit (항목 5) ─────────────────────────────────────────

    @Test
    void getSchedule_DONE_밴드면_canEdit_false() {
        User user = createUser(1L);
        Band band = createBand(50L, false);
        setField(band, "status", BandStatus.DONE);
        BandMember member = BandMember.create(user, band, BandRole.MEMBER);
        setId(member, 500L);

        Place place = createPlace(5000L, "ext-p1", "Place", PlaceCategory.CULTURE, 37.5, 127.0, null);
        Schedule schedule = Schedule.create(band, place, 1, 1, LocalTime.of(10, 0), 60, null,
                false, false, false, false, false);
        setId(schedule, 5001L);

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandRepository.findByIdAndIsDeletedFalse(50L)).thenReturn(Optional.of(band));
        when(bandMemberRepository.findByBandIdAndUserId(50L, 1L)).thenReturn(Optional.of(member));
        when(scheduleRepository.findByBandIdOrderByDayNumberAscSlotOrderAsc(50L))
                .thenReturn(List.of(schedule));

        ScheduleResponse response = scheduleService.getSchedule(1L, 50L);

        assertThat(response.canEdit()).isFalse();
        assertThat(response.editingUserId()).isNull();
    }

    @Test
    void getSchedule_joined_after_voting_멤버면_canEdit_false() {
        User user = createUser(1L);
        Band band = createBand(51L, false);
        BandMember member = BandMember.create(user, band, BandRole.MEMBER);
        setId(member, 510L);
        setField(member, "joinedAfterVoting", true);

        Place place = createPlace(5100L, "ext-p2", "Place2", PlaceCategory.CULTURE, 37.5, 127.0, null);
        Schedule schedule = Schedule.create(band, place, 1, 1, LocalTime.of(10, 0), 60, null,
                false, false, false, false, false);
        setId(schedule, 5101L);

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandRepository.findByIdAndIsDeletedFalse(51L)).thenReturn(Optional.of(band));
        when(bandMemberRepository.findByBandIdAndUserId(51L, 1L)).thenReturn(Optional.of(member));
        when(scheduleRepository.findByBandIdOrderByDayNumberAscSlotOrderAsc(51L))
                .thenReturn(List.of(schedule));

        ScheduleResponse response = scheduleService.getSchedule(1L, 51L);

        assertThat(response.canEdit()).isFalse();
    }

    @Test
    void getSchedule_편집락_없고_일반_멤버면_canEdit_true() {
        User user = createUser(1L);
        Band band = createBand(52L, false);  // 기본 status=PLANNING
        BandMember member = BandMember.create(user, band, BandRole.MEMBER);
        setId(member, 520L);

        Place place = createPlace(5200L, "ext-p3", "Place3", PlaceCategory.CULTURE, 37.5, 127.0, null);
        Schedule schedule = Schedule.create(band, place, 1, 1, LocalTime.of(10, 0), 60, null,
                false, false, false, false, false);
        setId(schedule, 5201L);

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandRepository.findByIdAndIsDeletedFalse(52L)).thenReturn(Optional.of(band));
        when(bandMemberRepository.findByBandIdAndUserId(52L, 1L)).thenReturn(Optional.of(member));
        when(scheduleRepository.findByBandIdOrderByDayNumberAscSlotOrderAsc(52L))
                .thenReturn(List.of(schedule));

        ScheduleResponse response = scheduleService.getSchedule(1L, 52L);

        assertThat(response.canEdit()).isTrue();
        assertThat(response.editingUserId()).isNull();
        assertThat(response.editingUserName()).isNull();
    }

    // ── 공통 헬퍼 ─────────────────────────────────────────────────────────────

    private void setId(Object target, Long id) {
        setField(target, "id", id);
    }

    /** 클래스 계층을 순회하며 필드를 찾아 값을 설정한다. */
    private void setField(Object target, String fieldName, Object value) {
        try {
            Class<?> clazz = target.getClass();
            while (clazz != null) {
                try {
                    Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    field.set(target, value);
                    return;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            throw new RuntimeException("Field not found: " + fieldName);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

