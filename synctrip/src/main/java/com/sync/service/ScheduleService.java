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
import com.sync.dto.schedule.PlanBResponse;
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

        List<VoteInfo> voteInfos = new ArrayList<>(votes.stream()
                .map(v -> new VoteInfo(v.getPlace().getId(), v.getUser().getId(), v.getResult()))
                .toList());

        // 본인 장바구니 장소에 투표 기록이 없으면 result=0(자동 LIKE)으로 보완 — DB 저장 없이 알고리즘 전용
        Set<String> votedKeys = votes.stream()
                .map(v -> v.getUser().getId() + ":" + v.getPlace().getId())
                .collect(Collectors.toSet());
        Set<Long> votingMemberIds = members.stream()
                .map(m -> m.getUser().getId())
                .collect(Collectors.toSet());
        for (PlaceBookmark bm : bookmarks) {
            if (!votingMemberIds.contains(bm.getUser().getId())) continue;
            String key = bm.getUser().getId() + ":" + bm.getPlace().getId();
            if (!votedKeys.contains(key)) {
                voteInfos.add(new VoteInfo(bm.getPlace().getId(), bm.getUser().getId(), 0));
            }
        }

        Map<Long, OpeningHours> openingHoursById = band.isOverseas()
                ? buildOpeningHoursMap(bookmarks)
                : Map.of();

        AlgorithmInput input = new AlgorithmInput(
                group, memberInfos, placeInfos, voteInfos, null, openingHoursById);

        // 3. 알고리즘 실행
        AlgorithmResult result = AlgorithmService.compute(input);

        if (result.step3Result().daySchedules().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "일정 생성 결과가 없습니다. 장소 또는 투표 데이터를 확인해주세요.");
        }

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

    @Transactional(readOnly = true)
    public List<PlanBResponse> getPlanBRecommendations(Long userId, Long bandId, Long targetPlaceId) {
        loadActiveUser(userId);
        Band band = loadBand(bandId);
        requireMembership(bandId, userId);

        Place targetPlace = placeRepository.findById(targetPlaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "대체할 기준 장소를 찾을 수 없습니다."));

        // 현재 일정에 포함된 장소 ID 수집 (중복 추천 방지)
        Set<Long> scheduledPlaceIds = scheduleRepository.findByBandIdOrderByDayNumberAscSlotOrderAsc(bandId).stream()
                .filter(s -> s.getPlace() != null)
                .map(s -> s.getPlace().getId())
                .collect(Collectors.toSet());

        List<ScheduleAlt> alts = scheduleAltRepository.findByBandIdOrderByPriorityScoreDesc(bandId);
        
        List<PlanBResponse> candidates = new ArrayList<>();
        
        for (ScheduleAlt alt : alts) {
            if (scheduledPlaceIds.contains(alt.getPlace().getId())) continue;
            if (alt.getCategory() != com.sync.domain.place.PlaceCategory.valueOf(targetPlace.getCategory().name())) continue;

            double distKm = haversine(
                    targetPlace.getLatitude(), targetPlace.getLongitude(),
                    alt.getPlace().getLatitude(), alt.getPlace().getLongitude()
            );

            // 반경 1km 이내 제한 (알고리즘 상수는 PLANB_MAX_DIST_KM = 1.0)
            if (distKm > 1.0) continue;

            double geoScore = Math.max(0.0, 1.0 - distKm / 1.0);
            // 점수 계산: 투표 점수 60% + 거리 점수 40% (알고리즘 기본값)
            double recommendScore = alt.getPriorityScore() * 0.6 + geoScore * 0.4;

            candidates.add(new PlanBResponse(
                    alt.getPlace().getId(),
                    alt.getCategory(),
                    recommendScore,
                    distKm,
                    false, // alt는 overflow가 아님
                    toPlaceInfo(alt.getPlace())
            ));
        }

        candidates.sort((a, b) -> Double.compare(b.recommendScore(), a.recommendScore()));

        // 최대 3개 추천
        int limit = Math.min(candidates.size(), 3);
        return candidates.subList(0, limit);
    }

    @Transactional
    public void startEditing(Long userId, Long bandId) {
        loadActiveUser(userId);
        Band band = loadBand(bandId);
        requireMembership(bandId, userId);

        if (band.isEditingByOther(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "다른 사용자가 편집 중입니다.");
        }

        band.startEditing(userId);
        bandRepository.save(band);
    }

    @Transactional
    public void finishEditing(Long userId, Long bandId) {
        loadActiveUser(userId);
        Band band = loadBand(bandId);
        
        // 본인이 잠금을 가지고 있을 때만 해제 가능
        if (band.getCurrentlyEditingUserId() != null && band.getCurrentlyEditingUserId().equals(userId)) {
            band.finishEditing();
            bandRepository.save(band);
        }
    }

    @Transactional
    public void swapSchedulePlace(Long userId, Long bandId, Long scheduleId, Long newPlaceId) {
        loadActiveUser(userId);
        Band band = loadBand(bandId);
        requireMembership(bandId, userId);
        requireEditingLock(band, userId);

        // 1. 교체할 일정 슬롯 확인
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "수정할 일정 슬롯을 찾을 수 없습니다."));

        if (!schedule.getBand().getId().equals(bandId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "해당 밴드의 일정이 아닙니다.");
        }

        Place oldPlace = schedule.getPlace();
        Place newPlace = placeRepository.findById(newPlaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "새로운 장소 정보를 찾을 수 없습니다."));

        // 2. 예비 목록(ScheduleAlt)에서 새 장소 확인
        ScheduleAlt altEntry = scheduleAltRepository.findByBandIdAndPlaceId(bandId, newPlaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "예비 목록에 없는 장소는 교체할 수 없습니다."));

        // 3. 상호 교체 수행 (Swap)
        // 일정 슬롯 업데이트
        schedule.updatePlace(newPlace);
        scheduleRepository.save(schedule);

        // 예비 목록 업데이트 (기존 장소를 예비 목록으로 보냄 -> 되돌리기 가능)
        altEntry.updatePlace(oldPlace);
        scheduleAltRepository.save(altEntry);
        
        log.info("일정 상호 교체 완료: band={}, slot={}, {} <-> {}", 
                bandId, scheduleId, oldPlace.getName(), newPlace.getName());
    }

    private void requireEditingLock(Band band, Long userId) {
        if (band.isEditingByOther(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "다른 사용자가 편집 중입니다. 편집 시작을 먼저 해주세요.");
        }
        if (band.getCurrentlyEditingUserId() == null || !band.getCurrentlyEditingUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "편집 권한이 없습니다. 편집 시작 버튼을 눌러주세요.");
        }
    }

    private static double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
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
