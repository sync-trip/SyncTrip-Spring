package com.sync.repository;

import com.sync.domain.band.BandStatus;
import com.sync.domain.vote.GroupVoteInfo;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupVoteInfoRepository extends JpaRepository<GroupVoteInfo, Long> {
    Optional<GroupVoteInfo> findByBandId(Long bandId);

    /**
     * 1시간 타임아웃 초과 + 아직 마감되지 않은 투표 조회
     * VoteScheduler가 주기적으로 호출한다.
     */
    @Query("SELECT gvi FROM GroupVoteInfo gvi JOIN FETCH gvi.band b WHERE b.status = :status AND gvi.voteStartedAt <= :deadline AND gvi.voteEndedAt IS NULL")
    List<GroupVoteInfo> findTimedOutVotes(@Param("status") BandStatus status, @Param("deadline") LocalDateTime deadline);
}
