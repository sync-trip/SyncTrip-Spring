package com.sync.service;

import com.sync.algorithm.AlgorithmInput;
import com.sync.algorithm.AlgorithmResult;
import com.sync.algorithm.AlgorithmService;
import com.sync.algorithm.TravelStyle;
import com.sync.algorithm.step1.GroupInfo;
import com.sync.algorithm.step1.MemberInfo;
import com.sync.algorithm.step1.PlaceInfo;
import com.sync.algorithm.step1.VoteInfo;
import com.sync.algorithm.step3.DaySchedule;
import com.sync.algorithm.step3.ScheduledPlace;
import com.sync.domain.band.Band;
import com.sync.domain.band.BandMember;
import com.sync.domain.place.Place;
import com.sync.domain.place.PlaceBookmark;
import com.sync.domain.schedule.Schedule;
import com.sync.domain.schedule.ScheduleAlt;
import com.sync.domain.vote.Vote;
import com.sync.repository.BandMemberRepository;
import com.sync.repository.PlaceBookmarkRepository;
import com.sync.repository.PlaceRepository;
import com.sync.repository.ScheduleAltRepository;
import com.sync.repository.ScheduleRepository;
import com.sync.repository.VoteRepository;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ScheduleGenerationService {

    private final BandMemberRepository bandMemberRepository;
    private final PlaceRepository placeRepository;
    private final PlaceBookmarkRepository placeBookmarkRepository;
    private final VoteRepository voteRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleAltRepository scheduleAltRepository;

    public ScheduleGenerationService(BandMemberRepository bandMemberRepository,
                                     PlaceRepository placeRepository,
                                     PlaceBookmarkRepository placeBookmarkRepository,
                                     VoteRepository voteRepository,
                                     ScheduleRepository scheduleRepository,
                                     ScheduleAltRepository scheduleAltRepository) {
        this.bandMemberRepository = bandMemberRepository;
        this.placeRepository = placeRepository;
        this.placeBookmarkRepository = placeBookmarkRepository;
        this.voteRepository = voteRepository;
        this.scheduleRepository = scheduleRepository;
        this.scheduleAltRepository = scheduleAltRepository;
    }

    public void generate(Band band) {
        Long bandId = band.getId();

        // ── 1. DB에서 데이터 조회 ───────────────────────────────────────
        // joined_after_voting 멤버는 투표에 참여하지 않으므로 제외
        List<BandMember> members = bandMemberRepository.findByBandId(bandId)
                .stream()
                .filter(m -> !m.isJoinedAfterVoting())
                .toList();

        List<Place> places = placeRepository.findAllByBandId(bandId);
        List<Vote> votes = voteRepository.findByBandId(bandId);
        List<PlaceBookmark> bookmarks = placeBookmarkRepository.findByBandId(bandId);

        // 장소별 첫 번째 북마크 유저 (PlaceInfo.bookmarkedBy 용)
        Map<Long, Long> firstBookmarkerByPlaceId = bookmarks.stream()
                .collect(Collectors.toMap(
                        pb -> pb.getPlace().getId(),
                        pb -> pb.getUser().getId(),
                        (first, second) -> first  // 중복 시 먼저 담은 사람 유지
                ));

        // ── 2. AlgorithmInput 조립 ──────────────────────────────────────
        GroupInfo groupInfo = toGroupInfo(band);

        List<MemberInfo> memberInfos = members.stream()
                .map(this::toMemberInfo)
                .toList();

        List<PlaceInfo> placeInfos = places.stream()
                .map(p -> toPlaceInfo(p, firstBookmarkerByPlaceId))
                .toList();

        List<VoteInfo> voteInfos = votes.stream()
                .map(v -> new VoteInfo(v.getPlace().getId(), v.getUser().getId(), v.getResult()))
                .toList();

        AlgorithmInput input = new AlgorithmInput(
                groupInfo,
                memberInfos,
                placeInfos,
                voteInfos,
                null,       // dayStartTime — SimpleTsp 기본값 사용
                Map.of()    // openingHoursById — 국내는 빈 맵, 해외는 추후 구현
        );

        // ── 3. 알고리즘 실행 (Step1 → Step2 → Step3) ────────────────────
        AlgorithmResult result = AlgorithmService.compute(input);

        // ── 4. 결과 저장 ────────────────────────────────────────────────
        Map<Long, Place> placeById = places.stream()
                .collect(Collectors.toMap(Place::getId, p -> p));

        saveSchedules(band, result, placeById);
        saveScheduleAlts(band, result, placeById);
    }

    private void saveSchedules(Band band, AlgorithmResult result, Map<Long, Place> placeById) {
        List<Schedule> schedules = new ArrayList<>();

        for (DaySchedule daySchedule : result.step3Result().daySchedules()) {
            List<ScheduledPlace> scheduledPlaces = daySchedule.places();
            for (int i = 0; i < scheduledPlaces.size(); i++) {
                ScheduledPlace sp = scheduledPlaces.get(i);
                Place place = placeById.get(sp.placeId());

                Integer travelTime = null;
                if (i > 0) {
                    LocalTime prevEnd = scheduledPlaces.get(i - 1).endTime();
                    travelTime = (int) Duration.between(prevEnd, sp.startTime()).toMinutes();
                }

                schedules.add(Schedule.create(
                        band, place,
                        sp.day(), sp.orderInDay(),
                        sp.startTime(), sp.estimatedDuration(),
                        travelTime
                ));
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

    private GroupInfo toGroupInfo(Band band) {
        return new GroupInfo(
                band.getId(),
                band.getDestinationLat(),
                band.getDestinationLng(),
                TravelStyle.valueOf(band.getTravelStyle().name()),
                band.getStartDate(),
                band.getEndDate(),
                band.isOverseas()
        );
    }

    private MemberInfo toMemberInfo(BandMember member) {
        return new MemberInfo(
                member.getUser().getId(),
                member.getRole().name(),
                member.isReady()
        );
    }

    private PlaceInfo toPlaceInfo(Place place, Map<Long, Long> firstBookmarkerByPlaceId) {
        long bookmarkedBy = firstBookmarkerByPlaceId.getOrDefault(place.getId(), 0L);
        com.sync.algorithm.PlaceCategory algoCategory =
                com.sync.algorithm.PlaceCategory.valueOf(place.getCategory().name());

        return new PlaceInfo(
                place.getId(),
                bookmarkedBy,
                place.getName(),
                algoCategory,
                place.getDensityPoint(),
                place.getEstimatedDuration(),
                place.getLatitude(),
                place.getLongitude()
        );
    }
}
