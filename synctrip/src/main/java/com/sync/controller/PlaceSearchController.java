package com.sync.controller;

import com.sync.common.annotation.LoginUser;
import com.sync.dto.place.PlaceSearchResult;
import com.sync.service.PlaceSearchService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 밴드 없이 위치 기반으로 장소를 검색하는 컨트롤러.
 * 밴드 생성 전 숙소 선택 단계에서 사용된다.
 */
@RestController
@RequestMapping("/api/places")
public class PlaceSearchController {

    private final PlaceSearchService placeSearchService;

    public PlaceSearchController(PlaceSearchService placeSearchService) {
        this.placeSearchService = placeSearchService;
    }

    /**
     * 위도·경도 기반 장소 검색 — 밴드 생성 전 숙소 선택 단계에서 호출.
     *
     * @param userId  JWT에서 추출한 현재 사용자 ID
     * @param keyword 검색 키워드 (필수, 없으면 400)
     * @param lat     검색 중심 위도
     * @param lng     검색 중심 경도
     */
    @GetMapping("/search")
    public ResponseEntity<List<PlaceSearchResult>> searchPlaces(
            @LoginUser Long userId,
            @RequestParam String keyword,
            @RequestParam double lat,
            @RequestParam double lng) {
        List<PlaceSearchResult> results = placeSearchService.searchPlacesForLocation(userId, keyword, lat, lng);
        return ResponseEntity.ok(results);
    }
}