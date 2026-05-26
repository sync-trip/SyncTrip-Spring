package com.sync.scheduler;

import com.sync.domain.band.BandStatus;
import com.sync.domain.vote.GroupVoteInfo;
import com.sync.repository.GroupVoteInfoRepository;
import com.sync.service.BandService;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 투표 1시간 타임아웃 스케줄러 — 1분마다 VOTING 상태 밴드를 점검해 자동 마감
@Component
public class VoteScheduler {

    private static final Logger log = LoggerFactory.getLogger(VoteScheduler.class);
    private static final long VOTE_TIMEOUT_HOURS = 1;

    private final GroupVoteInfoRepository groupVoteInfoRepository;
    private final BandService bandService;

    public VoteScheduler(GroupVoteInfoRepository groupVoteInfoRepository, BandService bandService) {
        this.groupVoteInfoRepository = groupVoteInfoRepository;
        this.bandService = bandService;
    }

    // 1분마다 실행 — 1시간 초과한 투표를 자동 마감
    @Scheduled(fixedDelay = 60_000)
    public void closeTimedOutVotes() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(VOTE_TIMEOUT_HOURS);
        List<GroupVoteInfo> timedOut = groupVoteInfoRepository.findTimedOutVotes(BandStatus.VOTING, deadline);

        for (GroupVoteInfo info : timedOut) {
            Long bandId = info.getBand().getId();
            log.info("투표 타임아웃 자동 마감: bandId={}", bandId);
            try {
                bandService.finishVoting(bandId);
            } catch (Exception e) {
                // 개별 밴드 실패가 전체 스케줄러를 멈추지 않도록 예외를 격리
                log.error("투표 자동 마감 실패: bandId={}, error={}", bandId, e.getMessage());
                // 장바구니 없음 등으로 마감 불가한 밴드는 PLANNING으로 복원해 반복 실패 방지
                try {
                    bandService.rollbackVotingToPlanning(bandId);
                    log.warn("투표 마감 실패 밴드를 PLANNING으로 복원: bandId={}", bandId);
                } catch (Exception rollbackEx) {
                    log.error("PLANNING 복원 실패: bandId={}, error={}", bandId, rollbackEx.getMessage());
                }
            }
        }
    }
}
