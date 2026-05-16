package com.sync.controller;

import com.sync.common.annotation.LoginUser;
import com.sync.domain.place.PlaceCategory;
import com.sync.dto.place.PlaceSearchResult;
import com.sync.service.PlaceSearchService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bands/{bandId}/places")
public class PlaceController {

    private final PlaceSearchService placeSearchService;

    public PlaceController(PlaceSearchService placeSearchService) {
        this.placeSearchService = placeSearchService;
    }

    /**
     * 여행지 주변 장소 검색 (해외 전용 — Google Places API)
     *
     * @param category    장소 카테고리 필터 (FOOD/CULTURE/ACTIVITY/SHOPPING/NATURE, 미입력 시 전체)
     * @param radiusMeters 검색 반경 (미터, 기본 5000)
     */
    @GetMapping("/search")
    public ResponseEntity<List<PlaceSearchResult>> searchPlaces(
            @LoginUser Long userId,
            @PathVariable Long bandId,
            @RequestParam(required = false) PlaceCategory category,
            @RequestParam(defaultValue = "5000") double radiusMeters
    ) {
        return ResponseEntity.ok(
                placeSearchService.searchPlaces(userId, bandId, category, radiusMeters)
        );
    }
}
