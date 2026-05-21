package com.sync.repository;

import com.sync.domain.finance.GroupExchangeRate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupExchangeRateRepository extends JpaRepository<GroupExchangeRate, Long> {

    List<GroupExchangeRate> findByBandId(Long bandId);

    Optional<GroupExchangeRate> findByBandIdAndCurrency(Long bandId, String currency);
}
