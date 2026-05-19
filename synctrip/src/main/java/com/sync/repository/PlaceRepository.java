package com.sync.repository;

import com.sync.domain.place.Place;
import com.sync.domain.place.PlaceApiSource;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    Optional<Place> findByApiSourceAndExternalId(PlaceApiSource apiSource, String externalId);

    @Query("SELECT DISTINCT pb.place FROM PlaceBookmark pb WHERE pb.band.id = :bandId")
    List<Place> findAllByBandId(@Param("bandId") Long bandId);
}

