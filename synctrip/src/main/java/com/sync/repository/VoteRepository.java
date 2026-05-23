package com.sync.repository;

import com.sync.domain.vote.Vote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    boolean existsByBandIdAndUserIdAndPlaceId(Long bandId, Long userId, Long placeId);

    long countByBandIdAndUserId(Long bandId, Long userId);

    List<Vote> findByBandIdAndUserId(Long bandId, Long userId);

    // 알고리즘 입력 조립용 — 그룹 전체 투표 결과
    List<Vote> findByBandId(Long bandId);

    // 전원 투표 완료 감지용 — 밴드 전체 투표 수 카운트
    long countByBandId(Long bandId);
}
