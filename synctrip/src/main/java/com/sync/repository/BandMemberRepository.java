package com.sync.repository;

import com.sync.domain.band.Band;
import com.sync.domain.band.BandMember;
import com.sync.domain.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BandMemberRepository extends JpaRepository<BandMember, Long> {
    List<BandMember> findByBandId(Long bandId);
    boolean existsByBandAndUser(Band band, User user);
    Optional<BandMember> findByBandIdAndUserId(Long bandId, Long userId);
    long countByBand(Band band);
}
