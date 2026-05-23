package com.sync.service;

import com.sync.domain.band.Band;
import com.sync.domain.band.BandMember;
import com.sync.domain.band.BandRole;
import com.sync.domain.band.BandStatus;
import com.sync.domain.user.User;
import com.sync.config.BandInviteProperties;
import com.sync.domain.notification.NotificationType;
import com.sync.dto.band.BandCreateRequest;
import com.sync.dto.band.BandInviteCodeResponse;
import com.sync.dto.band.BandMemberResponse;
import com.sync.dto.band.BandReadyResponse;
import com.sync.dto.band.BandResponse;
import com.sync.dto.band.BandStatusTransitionResponse;
import com.sync.dto.band.BandUpdateRequest;
import com.sync.domain.vote.GroupVoteInfo;
import com.sync.dto.holiday.HolidayInfo;
import com.sync.dto.ws.ReadyEvent;
import com.sync.dto.ws.StatusEvent;
import com.sync.repository.BandMemberRepository;
import com.sync.repository.BandRepository;
import com.sync.repository.GroupVoteInfoRepository;
import com.sync.repository.UserRepository;
import java.util.List;
import java.util.stream.Collectors;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class BandService {

    private final BandRepository bandRepository;
    private final BandMemberRepository bandMemberRepository;
    private final UserRepository userRepository;
    private final BandInviteProperties bandInviteProperties;
    private final ScheduleService scheduleService;
    private final GroupVoteInfoRepository groupVoteInfoRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;
    private final HolidayService holidayService;

    public BandService(BandRepository bandRepository,
                       BandMemberRepository bandMemberRepository,
                       UserRepository userRepository,
                       BandInviteProperties bandInviteProperties,
                       ScheduleService scheduleService,
                       GroupVoteInfoRepository groupVoteInfoRepository,
                       SimpMessagingTemplate messagingTemplate,
                       NotificationService notificationService,
                       HolidayService holidayService) {
        this.bandRepository = bandRepository;
        this.bandMemberRepository = bandMemberRepository;
        this.userRepository = userRepository;
        this.bandInviteProperties = bandInviteProperties;
        this.scheduleService = scheduleService;
        this.groupVoteInfoRepository = groupVoteInfoRepository;
        this.messagingTemplate = messagingTemplate;
        this.notificationService = notificationService;
        this.holidayService = holidayService;
    }

    /**
     * 새로운 여행 밴드 생성
     * - 생성한 사용자는 자동으로 OWNER(방장) 역할을 가집니다.
     */
    public BandResponse createBand(Long userId, BandCreateRequest request) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        Band band = Band.create(
                user, request.name(), request.destination(), request.destinationLat(),
                request.destinationLng(), request.countryCode(), request.overseas(),
                request.startDate(), request.endDate(), request.travelStyle(),
                request.accommodationName(), request.accommodationLat(), request.accommodationLng()
        );
        bandRepository.save(band);

        BandMember member = BandMember.create(user, band, BandRole.OWNER);
        bandMemberRepository.save(member);

        return toBandResponse(band, true, 1);
    }

    /**
     * 초대 코드를 이용한 밴드 가입
     * - 만료된 코드, 정원 초과, 이미 가입된 경우 등을 검증합니다.
     */
    public BandResponse joinBand(Long userId, String inviteCode) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        Band band = bandRepository.findActiveByInviteCode(inviteCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "유효하지 않은 초대 코드입니다."));

        if (band.isInviteCodeExpired(java.time.LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "만료된 초대 코드입니다.");
        }
        if (bandMemberRepository.existsByBandAndUser(band, user)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 밴드입니다.");
        }
        if (bandMemberRepository.countByBand(band) >= band.getMaxMembers()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "밴드 정원이 가득 찼습니다.");
        }

        BandMember member = BandMember.create(user, band, BandRole.MEMBER);
        if (band.getStatus() != BandStatus.PLANNING) {
            member.markJoinedAfterVoting();
        }
        bandMemberRepository.save(member);

        // 새 멤버 합류 시 기존 멤버 전원에게 알림
        notificationService.notifyAll(band.getId(), NotificationType.MEMBER_JOINED,
                user.getName() + "님이 " + band.getName() + "에 합류했어요! 👋");

        // 해외 밴드이면 합류한 멤버 본인에게 공휴일 안내 알림
        if (band.isOverseas()) {
            List<HolidayInfo> holidays = holidayService.getHolidaysInRange(
                    band.getCountryCode(), band.getStartDate(), band.getEndDate());
            if (!holidays.isEmpty()) {
                notificationService.notify(user.getId(), band.getId(), NotificationType.HOLIDAY_WARNING,
                        holidayService.buildHolidayMessage(band, holidays));
            }
        }

        long memberCount = bandMemberRepository.countByBand(band);
        return toBandResponse(band, false, (int) memberCount);
    }

    /**
     * 밴드 삭제 (Soft Delete)
     * - 방장만 가능하며, 실제로 데이터를 지우지 않고 '삭제됨' 마킹만 합니다.
     */
    public void deleteBand(Long userId, Long bandId) {
        Band band = loadBandForOwner(userId, bandId);
        band.delete();
        bandRepository.save(band);
    }

    /**
     * 밴드 기본 정보 수정
     * - PLANNING(준비) 단계에서만 수정 가능합니다.
     */
    public BandResponse updateBand(Long userId, Long bandId, BandUpdateRequest request) {
        Band band = loadBandForOwner(userId, bandId);
        band.updateInfo(
                request.name(), request.destination(), request.destinationLat(),
                request.destinationLng(), request.countryCode(), request.overseas(),
                request.startDate(), request.endDate(),
                request.travelStyle(), request.accommodationName(),
                request.accommodationLat(), request.accommodationLng()
        );
        bandRepository.save(band);

        long memberCount = bandMemberRepository.countByBandId(bandId);
        return toBandResponse(band, true, (int) memberCount);
    }

    /**
     * 준비 완료(Ready) 상태 변경
     * - 모든 멤버가 Ready가 되면 자동으로 투표(VOTING) 단계로 넘어갑니다.
     */
    public BandReadyResponse markReady(Long userId, Long bandId) {
        return updateReadyState(userId, bandId, true);
    }

    public BandReadyResponse markNotReady(Long userId, Long bandId) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "준비완료는 취소할 수 없습니다.");
    }

    /**
     * 밴드 상태를 다음 단계로 강제 전환 (방장 전용)
     */
    public BandStatusTransitionResponse advanceBandStatus(Long userId, Long bandId) {
        Band band = loadBandForOwner(userId, bandId);
        BandStatus previousStatus = band.getStatus();

        band.advanceStatus();
        bandRepository.save(band);

        if (previousStatus == BandStatus.PLANNING) {
            groupVoteInfoRepository.save(GroupVoteInfo.start(band, true));
            // 방장이 직접 강제 시작한 경우 — 방장 본인은 제외
            notificationService.notifyAllExcept(band.getId(), userId, NotificationType.VOTE_STARTED,
                    band.getName() + " 여행 투표가 시작됐어요! 지금 바로 참여하세요 ✈️");
        } else if (previousStatus == BandStatus.VOTING) {
            finishVotingInternal(band);
            return new BandStatusTransitionResponse(band.getId(), previousStatus, band.getStatus());
        } else if (previousStatus == BandStatus.TRAVELLING) {
            // 여행 종료 — 전원에게 정산 유도 알림
            notificationService.notifyAll(band.getId(), NotificationType.TRIP_ENDED,
                    band.getName() + " 여행이 종료됐어요! 정산을 완료해보세요 💰");
        }

        messagingTemplate.convertAndSend("/topic/bands/" + band.getId() + "/status",
                new StatusEvent(band.getId(), band.getStatus()));

        return new BandStatusTransitionResponse(band.getId(), previousStatus, band.getStatus());
    }

    /**
     * 투표 마감 처리 — VoteService(전원 완료 감지), VoteScheduler(타임아웃)에서도 호출
     * 이미 VOTING이 아니면 조용히 무시하여 멱등성을 보장한다.
     */
    public void finishVoting(Long bandId) {
        Band band = bandRepository.findByIdAndIsDeletedFalse(bandId).orElse(null);
        if (band == null || band.getStatus() != BandStatus.VOTING) return;
        band.advanceStatus();
        bandRepository.save(band);
        finishVotingInternal(band);
    }

    // VOTING → GENERATING 전환 공통 처리 (GroupVoteInfo 마감 + 일정 생성 + WebSocket)
    private void finishVotingInternal(Band band) {
        groupVoteInfoRepository.findByBandId(band.getId()).ifPresent(info -> {
            info.end();
            groupVoteInfoRepository.save(info);
        });
        scheduleService.generateAutomated(band);
        messagingTemplate.convertAndSend("/topic/bands/" + band.getId() + "/status",
                new StatusEvent(band.getId(), band.getStatus()));
    }

    /**
     * 초대 코드 조회 및 만료 시 재발급
     */
    public BandInviteCodeResponse getOrRefreshInviteCode(Long userId, Long bandId) {
        Band band = loadBandForOwner(userId, bandId);

        if (band.isInviteCodeExpired(java.time.LocalDateTime.now())) {
            band.reissueInviteCode();
            bandRepository.save(band);
        }

        return new BandInviteCodeResponse(
                band.getId(), band.getInviteCode(), band.getInviteCodeExpiredAt(),
                buildInviteShareLink(band.getInviteCode()), buildDeepLink(band.getInviteCode())
        );
    }

    private String buildInviteShareLink(String inviteCode) {
        return bandInviteProperties.shareBaseUrl() + URLEncoder.encode(inviteCode, StandardCharsets.UTF_8);
    }

    private String buildDeepLink(String inviteCode) {
        return bandInviteProperties.deepLinkBaseUrl() + URLEncoder.encode(inviteCode, StandardCharsets.UTF_8);
    }

    private BandReadyResponse updateReadyState(Long userId, Long bandId, boolean ready) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        Band band = bandRepository.findByIdAndIsDeletedFalse(bandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "밴드를 찾을 수 없습니다."));

        if (band.getStatus() != BandStatus.PLANNING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ready 상태 변경은 준비 단계(PLANNING)에서만 가능합니다.");
        }

        BandMember member = bandMemberRepository.findByBandIdAndUserId(bandId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "밴드 멤버가 아닙니다."));

        if (member.isJoinedAfterVoting()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "투표 이후 합류한 멤버는 Ready 상태를 변경할 수 없습니다.");
        }

        // 장바구니에 장소가 1개 이상 있어야 준비 완료 가능
        if (ready && member.getBookmarkCount() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "장바구니에 장소를 1개 이상 담아야 준비 완료할 수 있습니다.");
        }

        member.updateReady(ready);
        bandMemberRepository.save(member);

        long totalCount = bandMemberRepository.countByBandId(bandId);
        long readyCount = bandMemberRepository.countByBandIdAndIsReadyTrue(bandId);
        boolean allReady = totalCount > 0 && totalCount == readyCount;

        if (ready && allReady) {
            band.advanceStatus();
            bandRepository.save(band);
            groupVoteInfoRepository.save(GroupVoteInfo.start(band, false));
            messagingTemplate.convertAndSend("/topic/bands/" + bandId + "/status",
                    new StatusEvent(bandId, band.getStatus()));
            // 방장 포함 전원에게 투표 시작 알림
            notificationService.notifyAll(bandId, NotificationType.VOTE_STARTED,
                    band.getName() + " 여행 투표가 시작됐어요! 지금 바로 참여하세요 ✈️");
        } else if (ready) {
            notificationService.notify(band.getOwner().getId(), bandId, NotificationType.MEMBER_READY,
                    user.getName() + " 님이 준비 완료했어요!");
        }

        messagingTemplate.convertAndSend("/topic/bands/" + bandId + "/ready",
                new ReadyEvent(user.getId(), member.isReady(), readyCount, totalCount, allReady, band.getStatus()));

        return new BandReadyResponse(band.getId(), user.getId(), member.isReady(), readyCount, totalCount, allReady, band.getStatus());
    }

    private Band loadBandForOwner(Long userId, Long bandId) {
        userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        Band band = bandRepository.findByIdAndIsDeletedFalse(bandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "밴드를 찾을 수 없습니다."));
        if (!band.getOwner().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "방장만 가능한 기능입니다.");
        }
        return band;
    }

    private BandResponse toBandResponse(Band band, boolean isOwner, int memberCount) {
        return new BandResponse(
                band.getId(), band.getName(), band.getDestination(),
                band.getStartDate(), band.getEndDate(), band.getInviteCode(),
                band.getStatus(), isOwner, band.isOverseas(),
                band.getTravelStyle(), band.getAccommodationName(), memberCount
        );
    }

    /**
     * 특정 밴드의 모든 멤버 목록 조회
     */
    @Transactional(readOnly = true)
    public List<BandMemberResponse> getBandMembers(Long bandId) {
        return bandMemberRepository.findByBandId(bandId).stream()
                .map(m -> new BandMemberResponse(
                        m.getUser().getId(), m.getUser().getName(),
                        m.getUser().getProfileImageUrl(), m.getRole(), m.isReady(),
                        m.getJoinedAt(), m.getBookmarkCount()
                )).collect(Collectors.toList());
    }

    /**
     * 밴드 여행 기간 내 공휴일 목록 조회 (방장/멤버 공통)
     * - 국내 밴드(is_overseas=FALSE)는 빈 목록 반환
     */
    @Transactional(readOnly = true)
    public List<HolidayInfo> getBandHolidays(Long bandId) {
        Band band = bandRepository.findByIdAndIsDeletedFalse(bandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "밴드를 찾을 수 없습니다."));
        if (!band.isOverseas()) return List.of();
        return holidayService.getHolidaysInRange(band.getCountryCode(), band.getStartDate(), band.getEndDate());
    }

    /**
     * 내가 참여 중인 모든 활성 밴드 목록 조회
     */
    @Transactional(readOnly = true)
    public List<BandResponse> getMyBands(Long userId) {
        userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        return bandMemberRepository.findByUserId(userId).stream()
                .map(m -> {
                    long memberCount = bandMemberRepository.countByBandId(m.getBand().getId());
                    return toBandResponse(m.getBand(), m.getRole() == BandRole.OWNER, (int) memberCount);
                }).collect(Collectors.toList());
    }
}
