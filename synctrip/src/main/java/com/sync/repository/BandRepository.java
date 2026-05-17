package com.sync.repository;

import com.sync.domain.band.Band;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BandRepository extends JpaRepository<Band, Long> {

    Optional<Band> findByIdAndIsDeletedFalse(Long id);

    @Query("SELECT b FROM Band b WHERE b.inviteCode = :inviteCode AND b.isDeleted = false")
    Optional<Band> findActiveByInviteCode(String inviteCode);

    // 하위 호환성을 위해 유지하되, 내부적으로는 isDeleted 체크 필요
    Optional<Band> findByInviteCode(String inviteCode);
}
