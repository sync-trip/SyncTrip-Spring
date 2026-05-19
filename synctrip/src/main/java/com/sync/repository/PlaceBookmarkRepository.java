package com.sync.repository;

import com.sync.domain.place.PlaceBookmark;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * place_bookmarks 테이블 조회용 리포지토리
 * - 밴드/사용자 기준 장바구니 목록과 중복 여부를 확인한다.
 */
public interface PlaceBookmarkRepository extends JpaRepository<PlaceBookmark, Long> {

    List<PlaceBookmark> findByBandIdAndUserIdOrderByCreatedAtDesc(Long bandId, Long userId);

    Optional<PlaceBookmark> findByBandIdAndUserIdAndPlaceId(Long bandId, Long userId, Long placeId);

    long countByBandIdAndUserId(Long bandId, Long userId);

    boolean existsByBandIdAndUserIdAndPlaceId(Long bandId, Long userId, Long placeId);

    List<PlaceBookmark> findByBandId(Long bandId);

    void deleteByBandIdAndUserIdAndPlaceId(Long bandId, Long userId, Long placeId);
}

