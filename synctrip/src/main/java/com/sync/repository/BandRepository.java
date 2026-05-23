package com.sync.repository;

import com.sync.domain.band.Band;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 여행 밴드 전용 저장소
 */
public interface BandRepository extends JpaRepository<Band, Long> {

    /**
     * 삭제되지 않은 활성 밴드 조회
     */
    Optional<Band> findByIdAndIsDeletedFalse(Long id);

    /**
     * 초대 코드로 삭제되지 않은 활성 밴드 조회
     */
    @Query("SELECT b FROM Band b WHERE b.inviteCode = :inviteCode AND b.isDeleted = false")
    Optional<Band> findActiveByInviteCode(@Param("inviteCode") String inviteCode);

    /**
     * D-7 공휴일 스케줄러용: 특정 날짜에 시작하는 해외 활성 밴드 조회
     */
    @Query("SELECT b FROM Band b WHERE b.startDate = :startDate AND b.overseas = true AND b.isDeleted = false")
    List<Band> findOverseasBandsByStartDate(@Param("startDate") LocalDate startDate);
}
