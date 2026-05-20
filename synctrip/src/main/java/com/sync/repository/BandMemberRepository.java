package com.sync.repository;

import com.sync.domain.band.Band;
import com.sync.domain.band.BandMember;
import com.sync.domain.user.User;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 밴드 멤버 전용 저장소
 * - 모든 조회 쿼리는 Soft Delete(isDeleted = false)를 기본적으로 체크합니다.
 */
public interface BandMemberRepository extends JpaRepository<BandMember, Long> {

    /**
     * 특정 밴드에 속한 활성 멤버 목록 조회
     */
    @Query("SELECT bm FROM BandMember bm WHERE bm.band.id = :bandId AND bm.isDeleted = false")
    List<BandMember> findByBandId(@Param("bandId") Long bandId);

    /**
     * 사용자가 특정 밴드에 이미 가입되어 있는지 확인
     */
    @Query("SELECT CASE WHEN COUNT(bm) > 0 THEN true ELSE false END FROM BandMember bm WHERE bm.band = :band AND bm.user = :user AND bm.isDeleted = false")
    boolean existsByBandAndUser(@Param("band") Band band, @Param("user") User user);

    /**
     * 특정 밴드 내의 특정 유저 정보 조회
     */
    @Query("SELECT bm FROM BandMember bm WHERE bm.band.id = :bandId AND bm.user.id = :userId AND bm.isDeleted = false")
    Optional<BandMember> findByBandIdAndUserId(@Param("bandId") Long bandId, @Param("userId") Long userId);

    /**
     * 특정 밴드 내의 특정 유저 멤버 정보를 잠금 상태로 조회
     * - 장바구니 추가/삭제 시 bookmark_count 동시성 충돌을 막기 위해 사용한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT bm FROM BandMember bm WHERE bm.band.id = :bandId AND bm.user.id = :userId AND bm.isDeleted = false")
    Optional<BandMember> findByBandIdAndUserIdForUpdate(@Param("bandId") Long bandId, @Param("userId") Long userId);

    /**
     * 밴드의 현재 참여 인원 수 계산
     */
    @Query("SELECT COUNT(bm) FROM BandMember bm WHERE bm.band = :band AND bm.isDeleted = false")
    long countByBand(@Param("band") Band band);

    /**
     * 밴드 ID 기준 현재 참여 인원 수 계산
     */
    @Query("SELECT COUNT(bm) FROM BandMember bm WHERE bm.band.id = :bandId AND bm.isDeleted = false")
    long countByBandId(@Param("bandId") Long bandId);

    /**
     * 밴드 내 준비 완료(Ready) 상태인 인원 수 계산
     */
    @Query("SELECT COUNT(bm) FROM BandMember bm WHERE bm.band.id = :bandId AND bm.isReady = true AND bm.isDeleted = false")
    long countByBandIdAndIsReadyTrue(@Param("bandId") Long bandId);

    /**
     * 유저가 참여 중인 모든 활성 밴드 멤버 정보 조회 (삭제된 밴드 제외)
     */
    @Query("SELECT bm FROM BandMember bm WHERE bm.user.id = :userId AND bm.isDeleted = false AND bm.band.isDeleted = false")
    List<BandMember> findByUserId(@Param("userId") Long userId);
}
