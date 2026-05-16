package com.sync.repository;

import com.sync.domain.vote.GroupVoteInfo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupVoteInfoRepository extends JpaRepository<GroupVoteInfo, Long> {
    Optional<GroupVoteInfo> findByBandId(Long bandId);
}
