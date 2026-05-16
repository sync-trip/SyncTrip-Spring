package com.sync.service;

import com.sync.domain.band.Band;
import com.sync.domain.place.Place;
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
import com.sync.repository.ScheduleAltRepository;
import com.sync.repository.ScheduleRepository;
import com.sync.repository.UserRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleAltRepository scheduleAltRepository;
    private final BandRepository bandRepository;
    private final BandMemberRepository bandMemberRepository;
    private final UserRepository userRepository;

    public ScheduleService(ScheduleRepository scheduleRepository,
                           ScheduleAltRepository scheduleAltRepository,
                           BandRepository bandRepository,
                           BandMemberRepository bandMemberRepository,
                           UserRepository userRepository) {
        this.scheduleRepository = scheduleRepository;
        this.scheduleAltRepository = scheduleAltRepository;
        this.bandRepository = bandRepository;
        this.bandMemberRepository = bandMemberRepository;
        this.userRepository = userRepository;
    }

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

    public List<ScheduleAltResponse> getScheduleAlts(Long userId, Long bandId) {
        loadActiveUser(userId);
        loadBand(bandId);
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
        return bandRepository.findById(bandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "밴드를 찾을 수 없습니다."));
    }

    private void requireMembership(Long bandId, Long userId) {
        if (!bandMemberRepository.findByBandIdAndUserId(bandId, userId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "밴드 멤버만 일정을 조회할 수 있습니다.");
        }
    }
}
