package com.sync.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sync.algorithm.AlgorithmInput;
import com.sync.algorithm.AlgorithmResult;
import com.sync.algorithm.AlgorithmService;
import com.sync.algorithm.step1.GroupInfo;
import com.sync.algorithm.step1.MemberInfo;
import com.sync.algorithm.step1.PlaceInfo;
import com.sync.algorithm.step1.VoteInfo;
import com.sync.algorithm.step3.DaySchedule;
import com.sync.algorithm.step3.OpeningHours;
import com.sync.algorithm.step3.ScheduledPlace;
import com.sync.domain.band.Band;
import com.sync.domain.band.BandMember;
import com.sync.domain.band.BandStatus;
import com.sync.domain.place.Place;
import com.sync.domain.place.PlaceBookmark;
import com.sync.domain.schedule.Schedule;
import com.sync.domain.schedule.ScheduleAlt;
import com.sync.domain.user.User;
import com.sync.dto.schedule.ScheduleAltResponse;
import com.sync.dto.schedule.ScheduleDayResponse;
import com.sync.dto.schedule.SchedulePlaceInfo;
import com.sync.dto.schedule.ScheduleResponse;
import com.sync.dto.schedule.ScheduleSlotResponse;
import com.sync.repository.BandMemberRepository;
import com.sync.repository.BandRepository;
import com.sync.repository.PlaceBookmarkRepository;
import com.sync.repository.PlaceRepository;
import com.sync.repository.ScheduleAltRepository;
import com.sync.repository.ScheduleRepository;
import com.sync.repository.UserRepository;
import com.sync.repository.VoteRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    private final ScheduleRepository scheduleRepository;
    private final ScheduleAltRepository scheduleAltRepository;
    private final BandRepository bandRepository;
    private final BandMemberRepository bandMemberRepository;
    private final UserRepository userRepository;
    private final PlaceRepository placeRepository;
    private final PlaceBookmarkRepository placeBookmarkRepository;
    private final VoteRepository voteRepository;
    private final ObjectMapper objectMapper;

    public ScheduleService(ScheduleRepository scheduleRepository,
                           ScheduleAltRepository scheduleAltRepository,
                           BandRepository bandRepository,
                           BandMemberRepository bandMemberRepository,
                           UserRepository userRepository,
                           PlaceRepository placeRepository,
                           PlaceBookmarkRepository placeBookmarkRepository,
                           VoteRepository voteRepository,
                           ObjectMapper objectMapper) {
        this.scheduleRepository = scheduleRepository;
        this.scheduleAltRepository = scheduleAltRepository;
        this.bandRepository = bandRepository;
        this.bandMemberRepository = bandMemberRepository;
        this.userRepository = userRepository;
        this.placeRepository = placeRepository;
        this.placeBookmarkRepository = placeBookmarkRepository;
        this.voteRepository = voteRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 밴드 상태 전이 시 자동으로 호출되는 일정 생성
     */
    public void generateAutomated(Band band) {
        generateInternal(null, band.getId(), true);
    }

    /**
     * 컨트롤러에서 명시적으로 호출되는 일정 생성 (방장 전용)
     */
    public void generateManual(Long userId, Long bandId) {
        generateInternal(userId, bandId, false);
    }

    private void generateInternal(Long userId, Long bandId, boolean isAutomated) {
        Band band = bandRepository.findByIdAndIsDeletedFalse(bandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "밴드를 찾을 수 없습니다."));

        // 수동 생성 시 권한 및 상태 체크
        if (!isAutomated) {
            if (!band.getOwner().getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "방장만 일정을 생성할 수 있습니다.");
            }
            if (band.getStatus() != BandStatus.VOTING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "투표 중인 밴드만 일정을 생성할 수 있습니다.");
            }
        }

        // 1. 데이터 로드
        List<BandMember> members = bandMemberRepository.findByBandId(bandId).stream()
                .filter(m -> !m.isJoinedAfterVoting())
                .toList();
        List<Place> places = placeRepository.findAllByBandId(bandId);
        List<PlaceBookmark> bookmarks = placeBookmarkRepository.findByBandId(bandId);
        List<com.sync.domain.vote.Vote> votes = voteRepository.findByBandId(bandId);

        if (bookmarks.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "장바구니에 담긴 장소가 없습니다.");
        }

        // 2. 알고리즘 입력 조립
        GroupInfo group = new GroupInfo(
                band.getId(), band.getDestinationLat(), band.getDestinationLng(),
                com.sync.algorithm.TravelStyle.valueOf(band.getTravelStyle().name()),
                band.getStartDate(), band.getEndDate(), band.isOverseas()
        );

        List<MemberInfo> memberInfos = members.stream()
                .map(m -> new MemberInfo(m.getUser().getId(), m.getRole().name(), m.isReady()))
                .toList();

        Map<Long, Long> firstBookmarkerByPlaceId = bookmarks.stream()
                .collect(Collectors.toMap(
                        pb -> pb.getPlace().getId(),
                        pb -> pb.getUser().getId(),
                        (first, second) -> first
                ));

        List<PlaceInfo> placeInfos = places.stream()
                .map(p -> new PlaceInfo(
                        p.getId(),
                        firstBookmarkerByPlaceId.getOrDefault(p.getId(), 0L),
                        p.getName(),
                        com.sync.algorithm.PlaceCategory.valueOf(p.getCategory().name()),
                        p.getDensityPoint(),
                        p.getEstimatedDuration(),
                        p.getLatitude(),
                        p.getLongitude()
                ))
                .toList();

        List<VoteInfo> voteInfos = votes.stream()
                .map(v -> new VoteInfo(v.getPlace().getId(), v.getUser().getId(), v.getResult()))
                .toList();

        Map<Long, OpeningHours> openingHoursById = band.isOverseas()
                ? buildOpeningHoursMap(bookmarks)
                : Map.of();

        AlgorithmInput input = new AlgorithmInput(
                group, memberInfos, placeInfos, voteInfos, null, openingHoursById);

        // 3. 알고리즘 실행
        AlgorithmResult result = AlgorithmService.compute(input);

        // 4. 기존 데이터 삭제 및 저장 (Pool Swap 지원을 위해 모두 초기화 후 재생성)
        scheduleRepository.deleteAll(scheduleRepository.findByBandIdOrderByDayNumberAscSlotOrderAsc(bandId));
        scheduleAltRepository.deleteAll(scheduleAltRepository.findByBandIdOrderByPriorityScoreDesc(bandId));

        Map<Long, Place> placeById = places.stream()
                .collect(Collectors.toMap(Place::getId, p -> p));

        saveSchedules(band, result, placeById);
        saveScheduleAlts(band, result, placeById);
    }

    private void saveSchedules(Band band, AlgorithmResult result, Map<Long, Place> placeById) {
        List<Schedule> schedules = new ArrayList<>();
        for (DaySchedule day : result.step3Result().daySchedules()) {
            List<ScheduledPlace> slots = day.places();
            for (int i = 0; i < slots.size(); i++) {
                ScheduledPlace sp = slots.get(i);
                Place place = placeById.get(sp.placeId());
                if (place == null) continue;

                Integer travelTime = i > 0
                        ? (int) Duration.between(slots.get(i - 1).endTime(), sp.startTime()).toMinutes()
                        : null;

                schedules.add(Schedule.create(
                        band, place, sp.day(), sp.orderInDay(),
                        sp.startTime(), sp.estimatedDuration(), travelTime));
            }
        }
        scheduleRepository.saveAll(schedules);
    }

    private void saveScheduleAlts(Band band, AlgorithmResult result, Map<Long, Place> placeById) {
        List<ScheduleAlt> alts = result.step1Result().altPool().stream()
                .filter(alt -> placeById.containsKey(alt.placeId()))
                .map(alt -> ScheduleAlt.create(
                        band,
                        placeById.get(alt.placeId()),
                        (float) alt.priorityScore()
                ))
                .toList();
        scheduleAltRepository.saveAll(alts);
    }

    private Map<Long, OpeningHours> buildOpeningHoursMap(List<PlaceBookmark> bookmarks) {
        Map<Long, OpeningHours> result = new HashMap<>();
        for (PlaceBookmark pb : bookmarks) {
            Place p = pb.getPlace();
            if (p.getOpeningHoursJson() == null || result.containsKey(p.getId())) continue;
            try {
                Map<String, List<Map<String, String>>> parsed = objectMapper.readValue(
                        p.getOpeningHoursJson(), new TypeReference<>() {});
                parsed.values().stream()
                        .flatMap(List::stream)
                        .findFirst()
                        .ifPresent(period -> {
                            LocalTime open = LocalTime.parse(period.get("open"));
                            LocalTime close = LocalTime.parse(period.get("close"));
                            result.put(p.getId(), new OpeningHours(open, close));
                        });
            } catch (Exception e) {
                log.warn("opening_hours 파싱 실패: placeId={}", p.getId());
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public ScheduleResponse getSchedule(Long userId, Long bandId) {
        loadActiveUser(userId);
        Band band = loadBand(bandId);
        requireMembership(bandId, userId);

        List<Schedule> schedules =
                scheduleRepository.findByBandIdOrderByDayNumberAscSlotOrderAsc(bandId);

        if (schedules.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "아직 생성된 일정이 없습니다.");
        }

        Map<Integer, List<Schedule>> byDay = schedules.stream()
                .collect(Collectors.groupingBy(
                        Schedule::getDayNumber,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<ScheduleDayResponse> days = byDay.entrySet().stream()
                .map(e -> {
                    int dayNum = e.getKey();
                    LocalDate date = band.getStartDate().plusDays(dayNum - 1);
                    List<ScheduleSlotResponse> slots = e.getValue().stream()
                            .map(this::toSlotResponse)
                            .toList();
                    return new ScheduleDayResponse(dayNum, date, slots);
                })
                .toList();

        return new ScheduleResponse(bandId, band.getStartDate(), band.getEndDate(), days);
    }

    @Transactional(readOnly = true)
    public List<ScheduleAltResponse> getScheduleAlts(Long userId, Long bandId) {
        loadActiveUser(userId);
        Band band = loadBand(bandId);
        requireMembership(bandId, userId);

        return scheduleAltRepository.findByBandIdOrderByPriorityScoreDesc(bandId)
                .stream()
                .map(this::toAltResponse)
                .toList();
    }

    private ScheduleSlotResponse toSlotResponse(Schedule s) {
        return new ScheduleSlotResponse(
                s.getId(),
                s.getSlotOrder(),
                s.getStartTime(),
                s.getDurationMinutes(),
                s.getTravelTimeFromPrev(),
                toPlaceInfo(s.getPlace())
        );
    }

    private ScheduleAltResponse toAltResponse(ScheduleAlt a) {
        return new ScheduleAltResponse(
                a.getId(),
                a.getCategory(),
                a.getPriorityScore(),
                toPlaceInfo(a.getPlace())
        );
    }

    private SchedulePlaceInfo toPlaceInfo(Place p) {
        return new SchedulePlaceInfo(
                p.getId(),
                p.getApiSource(),
                p.getName(),
                p.getCategory(),
                p.getLatitude(),
                p.getLongitude(),
                p.getAddress(),
                p.getRating(),
                p.getThumbnailUrl()
        );
    }

    private User loadActiveUser(Long userId) {
        return userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private Band loadBand(Long bandId) {
        return bandRepository.findByIdAndIsDeletedFalse(bandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "밴드를 찾을 수 없습니다."));
    }

    private void requireMembership(Long bandId, Long userId) {
        if (bandMemberRepository.findByBandIdAndUserId(bandId, userId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "밴드 멤버만 일정을 조회할 수 있습니다.");
        }
    }
}
