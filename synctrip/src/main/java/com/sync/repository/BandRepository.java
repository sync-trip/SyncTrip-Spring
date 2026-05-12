package com.sync.repository;

import com.sync.domain.band.Band;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BandRepository extends JpaRepository<Band, Long> {
    Optional<Band> findByInviteCode(String inviteCode);
}
