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
import java.util.HashSet;
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
    /**
     * Plan B 단계형 반경 확장 설정
     * Stage 0: 1km (가장 가까운 후보 우선)
     * Stage 1: 2km (1km 이상, 2km 이하)
     * Stage 2: 3km (2km 초과, 3km 이하)
     *
     * 각 단계에서 후보가 많으면 그 단계에서만 뽑고,
     * 부족하면 다음 단계로 확장한다 (최대 3개 추천)
     */
    private static final List<Double> PLAN_B_STAGE_RADII_KM = List.of(1.0, 2.0, 3.0);
    /* Plan B 최대 추천 개수 (인수인계 문서 Line 136: 최대 후보 7개) */
    private static final int PLAN_B_MAX_RECOMMENDATIONS = 7;
    /* 추천 후보에 포함되기 위한 최소 우선순위 점수 (0.3 이상) */
    private static final double PLAN_B_MIN_PRIORITY_SCORE = 0.3;

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
        /**
         * Plan B 추천 GPS 기준 정리 (인수인계 문서 Line 134: "닫힌 장소 DB 위경도")
         *
         * GPS 기준점 = targetPlace (사용자가 교체 대상으로 지정한 일정 슬롯의 장소)
         *
         * 거리 계산: |currentPlace ← targetPlace까지의 거리 (Haversine)|
         *
         * 주의사항:
         * - 사용자의 "현재 실시간 GPS 위치"가 아님
         * - 모바일 앱이 실시간 GPS를 보내면 그걸 사용 가능 (현재는 미지원)
         * - 지금은 "교체할 장소 위치" 기준으로 근처 대체 장소 추천
         */
        /* 1. 사용자 및 밴드 권한 검증 */
        loadActiveUser(userId);
        Band band = loadBand(bandId);
        requireMembership(bandId, userId);

        /* 2. 기준이 되는 원래 장소 조회 (사용자가 "이 장소를 바꾸고 싶어"라고 지정한 장소) */
        Place targetPlace = placeRepository.findById(targetPlaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "대체할 기준 장소를 찾을 수 없습니다."));

        /* 3. GPS 기준점 확정: targetPlace의 위도/경도를 중심으로 주변 장소 탐색 */
        /* 현재 일정에 이미 포함된 장소 ID 수집 (중복 추천 방지)
           같은 장소를 여러 번 추천하지 않기 위함 */
        List<Schedule> schedules = scheduleRepository.findByBandIdOrderByDayNumberAscSlotOrderAsc(bandId);
        Set<Long> scheduledPlaceIds = schedules.stream()
                .filter(s -> s.getPlace() != null)
                .map(s -> s.getPlace().getId())
                .collect(Collectors.toSet());

        /* 4. 기준 장소가 현재 일정에 있는 경우, 그 슬롯의 방문 시각을 파악
           해외 밴드인 경우 대체 후보들의 영업시간이 그 시각과 겹치는지 확인할 때 사용 */
        Schedule targetSchedule = schedules.stream()
                .filter(s -> s.getPlace() != null && s.getPlace().getId().equals(targetPlaceId))
                .findFirst()
                .orElse(null);

        /* 5. 예비목록(ScheduleAlt)에서 추천 후보 로드 (이미 우선순위 순으로 정렬됨) */
        List<ScheduleAlt> alts = scheduleAltRepository.findByBandIdOrderByPriorityScoreDesc(bandId);

        /* 6. 최종 추천 리스트와 이미 추가된 장소들을 추적 */
        List<PlanBResponse> recommendations = new ArrayList<>();
        Set<Long> addedPlaceIds = new HashSet<>();

        /* 7. 단계별 반경 확장 시작: 1km 단계부터 차례로 후보를 탐색 */
        double previousRadius = 0.0;
        for (int stage = 0; stage < PLAN_B_STAGE_RADII_KM.size(); stage++) {
            double currentRadius = PLAN_B_STAGE_RADII_KM.get(stage);
            List<PlanBResponse> stageCandidates = new ArrayList<>();

            /* 8. 현재 단계의 반경 범위에 해당하는 후보들 필터링 */
        for (ScheduleAlt alt : alts) {
                /* 이미 일정에 포함된 장소면 제외 */
                if (scheduledPlaceIds.contains(alt.getPlace().getId())) continue;
                /* 이번 루프에서 이미 추가한 장소면 중복 방지 */
                if (addedPlaceIds.contains(alt.getPlace().getId())) continue;
                /* 카테고리 다르면 제외 (음식점을 음식점으로만 교체) */
                if (alt.getCategory() != com.sync.domain.place.PlaceCategory.valueOf(targetPlace.getCategory().name())) continue;
                /* 우선순위 점수가 너무 낮으면 제외 */
                if (alt.getPriorityScore() < PLAN_B_MIN_PRIORITY_SCORE) continue;

                /* 기준 장소와 이 후보 사이의 직선거리(km) 계산 */
                double distKm = haversine(
                        targetPlace.getLatitude(), targetPlace.getLongitude(),
                        alt.getPlace().getLatitude(), alt.getPlace().getLongitude()
                );

                /* 단계별 반경 필터: 이전 단계의 상한을 초과하고, 현재 단계의 상한 이내인 후보만 수집
                   예: Stage 0 (1km): 0~1km, Stage 1 (2km): 1~2km, Stage 2 (3km): 2~3km */
                if (distKm <= previousRadius || distKm > currentRadius) continue;

                /* 해외 밴드인 경우 방문 시각에 영업 중인지 확인
                   폐점되는 시간에 방문할 후보는 제외 */
                if (band.isOverseas() && targetSchedule != null
                        && !isOpenAtTargetSlot(alt.getPlace(), band, targetSchedule)) {
                    continue;
                }

                /* 거리 점수 계산 (가까울수록 높음, 최대 반경까지의 상대거리 기준)
                   거리가 0이면 1.0, 최대 반경(3km)이면 0.0 */
                double geoScore = Math.max(0.0, 1.0 - distKm / PLAN_B_STAGE_RADII_KM.get(PLAN_B_STAGE_RADII_KM.size() - 1));
                /* 최종 추천 점수 = 투표점수 60% + 거리점수 40%
                   투표점수(priorityScore): 알고리즘에서 계산한 이 장소의 선호도
                   거리점수(geoScore): 거리가 가까울수록 높음 */
                double recommendScore = alt.getPriorityScore() * 0.6 + geoScore * 0.4;

                /* 이 단계의 후보 리스트에 추가 */
                stageCandidates.add(new PlanBResponse(
                        alt.getPlace().getId(),
                        alt.getCategory(),
                        recommendScore,
                        distKm,
                        currentRadius,
                        stage,  /* 단계 번호 (0, 1, 2) */
                        false, // alt는 overflow가 아님
                        toPlaceInfo(alt.getPlace())
                ));
            }

            /* 9. 현재 단계의 후보들을 추천점수 내림차순으로 정렬 */
            stageCandidates.sort((a, b) -> Double.compare(b.recommendScore(), a.recommendScore()));

            /* 10. 현재 단계의 상위 후보들을 최종 추천 리스트에 추가 (최대 3개까지만) */
            for (PlanBResponse candidate : stageCandidates) {
                /* 이미 최대치(3개) 도달했으면 중단 */
                if (recommendations.size() >= PLAN_B_MAX_RECOMMENDATIONS) {
                    break;
                }
                /* 추천 리스트에 추가 */
                recommendations.add(candidate);
                /* 이미 추가한 장소 기록 (중복 방지) */
                addedPlaceIds.add(candidate.placeId());
            }

            /* 11. 충분한 추천을 모았으면 다음 단계 확장 불필요, 종료 */
            if (recommendations.size() >= PLAN_B_MAX_RECOMMENDATIONS) {
                break;
            }
            /* 다음 단계를 위해 현재 반경을 이전 반경으로 업데이트 */
            previousRadius = currentRadius;
        }

        /* 12. 최종 추천 반환 */
        return recommendations;
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

    private boolean isOpenAtTargetSlot(Place place, Band band, Schedule targetSchedule) {
        /* 해외 밴드인 경우, 대체 후보가 실제 방문 시각에 영업 중인지 확인하는 메서드 */

        /* 방문 시각 정보 없으면 영업시간 체크 불가, 일단 추천 가능으로 처리 */
        if (targetSchedule.getStartTime() == null) return true;

        /* 장소의 영업시간 정보 없으면 24시간 영업으로 간주 */
        String openingHoursJson = place.getOpeningHoursJson();
        if (openingHoursJson == null || openingHoursJson.isBlank()) return true;

        /* 1단계: 현재 방문 예정 날짜 계산 (여행 시작일 + 일정의 day_number) */
        LocalDate targetDate = band.getStartDate().plusDays(targetSchedule.getDayNumber() - 1L);

        /* 2단계: 요일을 Google Places API 포맷(MON, TUE, ...)으로 변환 */
        String dayKey = switch (targetDate.getDayOfWeek()) {
            case MONDAY -> "MON";
            case TUESDAY -> "TUE";
            case WEDNESDAY -> "WED";
            case THURSDAY -> "THU";
            case FRIDAY -> "FRI";
            case SATURDAY -> "SAT";
            case SUNDAY -> "SUN";
        };

        /* 3단계: JSON 파싱 및 해당 요일의 영업시간 정보 추출 */
        try {
            Map<String, List<Map<String, String>>> parsed = objectMapper.readValue(
                    openingHoursJson, new TypeReference<>() {});
            /* 예: { "MON": [{"open": "09:00", "close": "20:00"}], "TUE": [...], ... } */
            List<Map<String, String>> periods = parsed.get(dayKey);

            /* 이 요일의 영업시간이 없으면 폐점 (false 반환) */
            if (periods == null || periods.isEmpty()) return false;

            /* 4단계: 방문 예정 시각이 영업시간 범위에 포함되는지 확인 */
            LocalTime targetTime = targetSchedule.getStartTime();
            for (Map<String, String> period : periods) {
                String openText = period.get("open");
                String closeText = period.get("close");
                /* 영업시간 데이터 불완전하면 스킵 */
                if (openText == null || closeText == null) continue;

                /* 시작/종료 시각을 LocalTime으로 파싱 */
                LocalTime open = LocalTime.parse(openText);
                LocalTime close = LocalTime.parse(closeText);

                /* 자정을 넘어가는 영업시간 처리
                   예: 21:00 ~ 03:00 (다음날)인 경우 close(03:00) < open(21:00)
                   이 경우 targetTime이 21:00 이상 이거나 03:00 미만이면 영업 중 */
                if (close.isBefore(open)) {
                    /* 자정 넘어가는 영업 */
                    if (!targetTime.isBefore(open) || targetTime.isBefore(close)) {
                        return true;
                    }
                } else {
                    /* 일반적인 영업 (23:59 이내에 끝남) */
                    if (!targetTime.isBefore(open) && targetTime.isBefore(close)) {
                        return true;
                    }
                }
            }
            /* 모든 영업시간을 확인했는데 겹치는 게 없으면 폐점 */
            return false;
        } catch (Exception e) {
            /* JSON 파싱 실패 등 에러 발생 시 영업시간 정보 신뢰 불가,
               일단 추천 가능으로 처리 (에러로 추천 제외하는 것보다 낫다) */
            log.warn("Plan B 영업시간 파싱 실패: placeId={}", place.getId());
            return true;
        }
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
