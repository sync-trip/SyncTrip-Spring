package com.sync.dto.schedule;

import com.sync.domain.place.PlaceApiSource;
import com.sync.domain.place.PlaceCategory;

/** POST /schedule/add-search — 장소 검색 결과를 특정 Day에 직접 추가하는 요청 */
public record ScheduleAddFromSearchRequest(
        PlaceApiSource apiSource,
        String externalId,
        String name,
        PlaceCategory category,
        double latitude,
        double longitude,
        String address,
        Float rating,
        String thumbnailUrl,
        int targetDayNumber
) {}
