package com.sync.repository;

import com.sync.domain.schedule.Schedule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    List<Schedule> findByBandIdOrderByDayNumberAscSlotOrderAsc(Long bandId);
}
