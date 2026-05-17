package com.sync.service;

import com.sync.domain.band.Band;
import com.sync.domain.band.BandMember;
import com.sync.domain.band.BandRole;
import com.sync.domain.band.BandStatus;
import com.sync.domain.user.User;
import com.sync.config.BandInviteProperties;
import com.sync.dto.band.BandCreateRequest;
import com.sync.dto.band.BandInviteCodeResponse;
import com.sync.dto.band.BandMemberResponse;
import com.sync.dto.band.BandReadyResponse;
import com.sync.dto.band.BandResponse;
import com.sync.dto.band.BandStatusTransitionResponse;
import com.sync.dto.band.BandUpdateRequest;
import com.sync.domain.vote.GroupVoteInfo;
import com.sync.dto.ws.ReadyEvent;
import com.sync.dto.ws.StatusEvent;
import com.sync.repository.BandMemberRepository;
import com.sync.repository.BandRepository;
import com.sync.repository.GroupVoteInfoRepository;
import com.sync.repository.PlaceBookmarkRepository;
import com.sync.repository.ScheduleAltRepository;
import com.sync.repository.ScheduleRepository;
import com.sync.repository.UserRepository;
import com.sync.repository.VoteRepository;
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
    private final ScheduleRepository scheduleRepository;
    private final ScheduleAltRepository scheduleAltRepository;
    private final VoteRepository voteRepository;
    private final PlaceBookmarkRepository placeBookmarkRepository;

    public BandService(BandRepository bandRepository,
                       BandMemberRepository bandMemberRepository,
                       UserRepository userRepository,
                       BandInviteProperties bandInviteProperties,
                       ScheduleService scheduleService,
                       GroupVoteInfoRepository groupVoteInfoRepository,
                       SimpMessagingTemplate messagingTemplate,
                       ScheduleRepository scheduleRepository,
                       ScheduleAltRepository scheduleAltRepository,
                       VoteRepository voteRepository,
                       PlaceBookmarkRepository placeBookmarkRepository) {
        this.bandRepository = bandRepository;
        this.bandMemberRepository = bandMemberRepository;
        this.userRepository = userRepository;
        this.bandInviteProperties = bandInviteProperties;
        this.scheduleService = scheduleService;
        this.groupVoteInfoRepository = groupVoteInfoRepository;
        this.messagingTemplate = messagingTemplate;
        this.scheduleRepository = scheduleRepository;
        this.scheduleAltRepository = scheduleAltRepository;
        this.voteRepository = voteRepository;
        this.placeBookmarkRepository = placeBookmarkRepository;
    }

    public BandResponse createBand(Long userId, BandCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        // 여행 정보를 모두 입력받아서 Band 생성
        Band band = Band.create(
                user,
                request.name(),
                request.destination(),
                request.destinationLat(),
                request.destinationLng(),
                request.countryCode(),
                request.overseas(),
                request.startDate(),
                request.endDate()
        );
        bandRepository.save(band);

        BandMember member = BandMember.create(user, band, BandRole.OWNER);
        bandMemberRepository.save(member);

        return new BandResponse(
                band.getId(),
                band.getName(),
                band.getDestination(),
                band.getStartDate(),
                band.getEndDate(),
                band.getInviteCode()
        );
    }

    public void joinBand(Long userId, String inviteCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        Band band = bandRepository.findActiveByInviteCode(inviteCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "유효하지 않은 초대 코드입니다."));

        if (band.getInviteCodeExpiredAt() != null && band.getInviteCodeExpiredAt().isBefore(java.time.LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "만료된 초대 코드입니다.");
        }

        if (bandMemberRepository.existsByBandAndUser(band, user)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 밴드입니다.");
        }

        if (bandMemberRepository.countByBand(band) >= band.getMaxMembers()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "밴드 정원이 가득 찼습니다.");
        }

        BandMember member = BandMember.create(user, band, BandRole.MEMBER);
        // PLANNING 이후에 들어온 멤버는 장바구니/투표 권한을 제한할 수 있도록 표시한다.
        if (band.getStatus() != BandStatus.PLANNING) {
            member.markJoinedAfterVoting();
        }
        bandMemberRepository.save(member);
    }

    public void deleteBand(Long userId, Long bandId) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        Band band = bandRepository.findById(bandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "밴드를 찾을 수 없습니다."));

        if (!band.getOwner().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "밴드는 방장만 삭제할 수 있습니다.");
        }

        // 소프트 삭제 처리
        band.delete();
        bandRepository.save(band);
    }

    public BandResponse updateBand(Long userId, Long bandId, BandUpdateRequest request) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        Band band = bandRepository.findById(bandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "밴드를 찾을 수 없습니다."));

        if (!band.getOwner().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "밴드 정보는 방장만 수정할 수 있습니다.");
        }

        band.updateInfo(
                request.name(),
                request.destination(),
                request.destinationLat(),
                request.destinationLng(),
                request.countryCode(),
                request.overseas(),
                request.startDate(),
                request.endDate()
        );
        bandRepository.save(band);

        return new BandResponse(
                band.getId(),
                band.getName(),
                band.getDestination(),
                band.getStartDate(),
                band.getEndDate(),
                band.getInviteCode()
        );
    }

    public BandReadyResponse markReady(Long userId, Long bandId) {
        return updateReadyState(userId, bandId, true);
    }

    public BandReadyResponse markNotReady(Long userId, Long bandId) {
        return updateReadyState(userId, bandId, false);
    }

    public BandStatusTransitionResponse advanceBandStatus(Long userId, Long bandId) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        Band band = bandRepository.findById(bandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "밴드를 찾을 수 없습니다."));

        if (!band.getOwner().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "밴드 상태는 방장만 전이할 수 있습니다.");
        }

        BandStatus previousStatus = band.getStatus();
        if (previousStatus.next() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "더 이상 전이할 수 없는 밴드 상태입니다.");
        }

        band.advanceStatus();
        bandRepository.save(band);

        if (previousStatus == BandStatus.PLANNING) {
            groupVoteInfoRepository.save(GroupVoteInfo.start(band, true));
        } else if (previousStatus == BandStatus.VOTING) {
            groupVoteInfoRepository.findByBandId(band.getId())
                    .ifPresent(info -> {
                        info.end();
                        groupVoteInfoRepository.save(info);
                    });
            scheduleService.generateAutomated(band);
        }

        messagingTemplate.convertAndSend("/topic/bands/" + band.getId() + "/status",
                new StatusEvent(band.getId(), band.getStatus()));

        return new BandStatusTransitionResponse(band.getId(), previousStatus, band.getStatus());
    }

    public BandInviteCodeResponse getOrRefreshInviteCode(Long userId, Long bandId) {
        // 1) 요청한 사용자가 존재하는지 먼저 확인한다.
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        // 2) 밴드를 찾아서 방장만 초대코드를 조회/갱신할 수 있도록 제한한다.
        Band band = bandRepository.findById(bandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "밴드를 찾을 수 없습니다."));

        if (!band.getOwner().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "밴드 초대코드는 방장만 재발급할 수 있습니다.");
        }

        // 3) 초대코드가 만료되었을 때만 새 코드와 새 만료 시간을 발급한다.
        if (band.isInviteCodeExpired(java.time.LocalDateTime.now())) {
            band.reissueInviteCode();
        }

        // 4) Android 앱에서 공유하기 좋도록 웹 링크와 딥링크를 모두 만든다.
        String inviteShareLink = buildInviteShareLink(band.getInviteCode());
        String inviteDeepLink = buildDeepLink(band.getInviteCode());

        bandRepository.save(band);

        return new BandInviteCodeResponse(
                band.getId(),
                band.getInviteCode(),
                band.getInviteCodeExpiredAt(),
                inviteShareLink,
                inviteDeepLink
        );
    }

    private String buildInviteShareLink(String inviteCode) {
        // 공유 링크에 초대코드를 안전하게 붙이기 위해 URL 인코딩을 적용한다.
        return bandInviteProperties.shareBaseUrl() + URLEncoder.encode(inviteCode, StandardCharsets.UTF_8);
    }

    private String buildDeepLink(String inviteCode) {
        // 딥링크는 앱 설치 시 바로 앱으로 들어오도록 커스텀 스킴을 사용한다.
        return bandInviteProperties.deepLinkBaseUrl() + URLEncoder.encode(inviteCode, StandardCharsets.UTF_8);
    }

    private BandReadyResponse updateReadyState(Long userId, Long bandId, boolean ready) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        Band band = bandRepository.findById(bandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "밴드를 찾을 수 없습니다."));

        if (band.getStatus() != BandStatus.PLANNING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ready 상태는 여행 준비 중에만 변경할 수 있습니다.");
        }

        BandMember member = bandMemberRepository.findByBandIdAndUserId(bandId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "밴드 멤버만 Ready 상태를 변경할 수 있습니다."));

        if (member.isJoinedAfterVoting()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "투표 이후 합류한 멤버는 Ready 상태를 변경할 수 없습니다.");
        }

        member.updateReady(ready);
        bandMemberRepository.save(member);

        long totalCount = bandMemberRepository.countByBandId(bandId);
        long readyCount = bandMemberRepository.countByBandIdAndIsReadyTrue(bandId);
        boolean allReady = totalCount > 0 && totalCount == readyCount;

        if (ready && allReady) {
            band.advanceStatus();  // PLANNING → VOTING
            bandRepository.save(band);
            groupVoteInfoRepository.save(GroupVoteInfo.start(band, false));
            messagingTemplate.convertAndSend("/topic/bands/" + bandId + "/status",
                    new StatusEvent(bandId, band.getStatus()));
        }

        ReadyEvent readyEvent = new ReadyEvent(
                user.getId(), member.isReady(), readyCount, totalCount, allReady, band.getStatus());
        messagingTemplate.convertAndSend("/topic/bands/" + bandId + "/ready", readyEvent);

        return new BandReadyResponse(
                band.getId(),
                user.getId(),
                member.isReady(),
                readyCount,
                totalCount,
                allReady,
                band.getStatus()
        );
    }

    @Transactional(readOnly = true)
    public List<BandMemberResponse> getBandMembers(Long bandId) {
        return bandMemberRepository.findByBandId(bandId).stream()
                .map(m -> new BandMemberResponse(
                        m.getUser().getId(),
                        m.getUser().getName(),
                        m.getUser().getProfileImageUrl(),
                        m.getRole(),
                        m.isReady()
                ))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BandResponse> getMyBands(Long userId) {
        userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        return bandMemberRepository.findByUserId(userId).stream()
                .map(m -> new BandResponse(
                        m.getBand().getId(),
                        m.getBand().getName(),
                        m.getBand().getDestination(),
                        m.getBand().getStartDate(),
                        m.getBand().getEndDate(),
                        m.getBand().getInviteCode()
                ))
                .collect(Collectors.toList());
    }
}
