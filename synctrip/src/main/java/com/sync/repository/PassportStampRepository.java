package com.sync.repository;

import com.sync.domain.stamp.PassportStamp;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PassportStampRepository extends JpaRepository<PassportStamp, Long> {

    List<PassportStamp> findByUserIdAndIsDeletedFalseOrderByStampedAtDesc(Long userId);
}
