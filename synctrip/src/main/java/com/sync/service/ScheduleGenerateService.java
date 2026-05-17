package com.sync.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sync.algorithm.AlgorithmInput;
import com.sync.algorithm.AlgorithmResult;
import com.sync.algorithm.AlgorithmService;
import com.sync.algorithm.step1.AltPoolPlace;
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
import com.sync.domain.vote.Vote;
import com.sync.repository.BandMemberRepository;
import com.sync.repository.BandRepository;
import com.sync.repository.PlaceBookmarkRepository;
import com.sync.repository.ScheduleAltRepository;
import com.sync.repository.ScheduleRepository;
import com.sync.repository.VoteRepository;
import java.time.Duration;
import java.time.LocalTime;
import java.util.HashMap;
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
public class ScheduleGenerateService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleGenerateService.class);

    private final BandRepository bandRepository;
    private final BandMemberRepository bandMemberRepository;
    private final PlaceBookmarkRepository placeBookmarkRepository;
    private final VoteRepository voteRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleAltRepository scheduleAltRepository;
    private final ObjectMapper objectMapper;

    public ScheduleGenerateService(BandRepository bandRepository,
                                   BandMemberRepository bandMemberRepository,
                                   PlaceBookmarkRepository placeBookmarkRepository,
                                   VoteRepository voteRepository,
                                   ScheduleRepository scheduleRepository,
                                   ScheduleAltRepository scheduleAltRepository,
                                   ObjectMapper objectMapper) {
        this.bandRepository = bandRepository;
        this.bandMemberRepository = bandMemberRepository;
        this.placeBookmarkRepository = placeBookmarkRepository;
        this.voteRepository = voteRepository;
        this.scheduleRepository = scheduleRepository;
        this.scheduleAltRepository = scheduleAltRepository;
        this.objectMapper = objectMapper;
    }

    public void generate(Long userId, Long bandId) {
        Band band = bandRepository.findById(bandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "밴드를 찾을 수 없습니다."));

        if (!band.getOwner().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "방장만 일정을 생성할 수 있습니다.");
        }
        if (band.getStatus() != BandStatus.VOTING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "투표 중인 밴드만 일정을 생성할 수 있습니다.");
        }

        List<BandMember> members = bandMemberRepository.findByBandId(bandId).stream()
                .filter(m -> !m.isJoinedAfterVoting())
                .toList();
        List<PlaceBookmark> bookmarks = placeBookmarkRepository.findByBandId(bandId);
        List<Vote> votes = voteRepository.findByBandId(bandId);

        if (bookmarks.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "장바구니에 담긴 장소가 없습니다.");
        }

        GroupInfo group = new GroupInfo(
                band.getId(), band.getDestinationLat(), band.getDestinationLng(),
                com.sync.algorithm.TravelStyle.valueOf(band.getTravelStyle().name()),
                band.getStartDate(), band.getEndDate(), band.isOverseas()
        );

        List<MemberInfo> memberInfos = members.stream()
                .map(m -> new MemberInfo(m.getUser().getId(), m.getRole().name(), m.isReady()))
                .toList();

        // 같은 장소를 여러 명이 담을 수 있으므로 placeId 기준으로 중복 제거
        List<PlaceInfo> placeInfos = bookmarks.stream()
                .collect(Collectors.toMap(
                        pb -> pb.getPlace().getId(),
                        pb -> {
                            Place p = pb.getPlace();
                            return new PlaceInfo(
                                    p.getId(),
                                    pb.getUser().getId(),
                                    p.getName(),
                                    com.sync.algorithm.PlaceCategory.valueOf(p.getCategory().name()),
                                    p.getDensityPoint(),
                                    p.getEstimatedDuration(),
                                    p.getLatitude(),
                                    p.getLongitude()
                            );
                        },
                        (a, b) -> a,
                        java.util.LinkedHashMap::new
                ))
                .values().stream().toList();

        List<VoteInfo> voteInfos = votes.stream()
                .map(v -> new VoteInfo(v.getPlace().getId(), v.getUser().getId(), v.getResult()))
                .toList();

        Map<Long, OpeningHours> openingHoursById = band.isOverseas()
                ? buildOpeningHoursMap(bookmarks)
                : Map.of();

        AlgorithmInput input = new AlgorithmInput(
                group, memberInfos, placeInfos, voteInfos, null, openingHoursById);

        AlgorithmResult result = AlgorithmService.compute(input);

        scheduleRepository.deleteAll(
                scheduleRepository.findByBandIdOrderByDayNumberAscSlotOrderAsc(bandId));
        scheduleAltRepository.deleteAll(
                scheduleAltRepository.findByBandIdOrderByPriorityScoreDesc(bandId));

        Map<Long, Place> placeById = bookmarks.stream()
                .collect(Collectors.toMap(
                        pb -> pb.getPlace().getId(),
                        PlaceBookmark::getPlace,
                        (a, b) -> a));

        for (DaySchedule day : result.step3Result().daySchedules()) {
            List<ScheduledPlace> slots = day.places();
            for (int i = 0; i < slots.size(); i++) {
                ScheduledPlace sp = slots.get(i);
                Place place = placeById.get(sp.placeId());
                if (place == null) {
                    log.warn("placeId={} 매핑 실패, 슬롯 건너뜀", sp.placeId());
                    continue;
                }
                Integer travelTime = i > 0
                        ? (int) Duration.between(slots.get(i - 1).endTime(), sp.startTime()).toMinutes()
                        : null;
                scheduleRepository.save(Schedule.create(
                        band, place, sp.day(), sp.orderInDay(),
                        sp.startTime(), sp.estimatedDuration(), travelTime));
            }
        }

        for (AltPoolPlace alt : result.step1Result().altPool()) {
            Place place = placeById.get(alt.placeId());
            if (place != null) {
                scheduleAltRepository.save(ScheduleAlt.create(band, place, (float) alt.priorityScore()));
            }
        }

        band.advanceStatus();
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
}
