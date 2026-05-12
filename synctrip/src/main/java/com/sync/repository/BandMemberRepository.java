package com.sync.repository;

import com.sync.domain.band.Band;
import com.sync.domain.band.BandMember;
import com.sync.domain.user.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BandMemberRepository extends JpaRepository<BandMember, Long> {
    List<BandMember> findByBandId(Long bandId);
    boolean existsByBandAndUser(Band band, User user);
    long countByBand(Band band);
}
