package com.sync.repository;

import com.sync.domain.album.AlbumPhoto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlbumPhotoRepository extends JpaRepository<AlbumPhoto, Long> {

    List<AlbumPhoto> findByBandIdAndIsDeletedFalseOrderByUploadedAtDesc(Long bandId);
}
