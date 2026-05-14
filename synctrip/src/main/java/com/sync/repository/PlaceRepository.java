package com.sync.repository;

import com.sync.domain.place.Place;
import com.sync.domain.place.PlaceApiSource;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * places 테이블 조회용 리포지토리
 * - 외부 API 소스와 externalId 조합으로 장소를 재사용한다.
 */
public interface PlaceRepository extends JpaRepository<Place, Long> {

    Optional<Place> findByApiSourceAndExternalId(PlaceApiSource apiSource, String externalId);
}

