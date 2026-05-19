package com.sync.repository;

import com.sync.domain.schedule.ScheduleAlt;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleAltRepository extends JpaRepository<ScheduleAlt, Long> {

    List<ScheduleAlt> findByBandIdOrderByPriorityScoreDesc(Long bandId);

    Optional<ScheduleAlt> findByBandIdAndPlaceId(Long bandId, Long placeId);
}
