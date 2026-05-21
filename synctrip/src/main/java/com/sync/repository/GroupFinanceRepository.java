package com.sync.repository;

import com.sync.domain.finance.GroupFinance;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupFinanceRepository extends JpaRepository<GroupFinance, Long> {

    Optional<GroupFinance> findByBandId(Long bandId);
}
