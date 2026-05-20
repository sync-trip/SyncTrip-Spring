package com.sync.dto.schedule;

import com.sync.domain.place.PlaceCategory;

/**
 * Plan B (대체 추천) 응답 DTO
 *
 * 사용자가 선택한 일정 슬롯의 장소를 다른 장소로 교체할 때,
 * 추천되는 대체 후보들의 정보를 담는다.
 *
 * 단계형 반경 확장 (1km → 2km → 3km)에 따라 어느 반경에서
 * 추천되었는지 프론트에서 표시할 수 있도록 메타데이터 포함.
 */
public record PlanBResponse(
         /* 대체 후보 장소의 ID */
        Long placeId,
         /* 카테고리 (FOOD, CULTURE 등) */
        PlaceCategory category,
         /* 최종 순위 점수 (투표점수 60% + 거리점수 40%) */
        double recommendScore,
         /* 기준 장소에서 이 후보까지의 직선거리 (km) */
        double distanceKmToTarget,
         /* 이 추천이 확정된 단계의 반경 (1.0, 2.0, 3.0km) */
        double searchRadiusKmUsed,
         /* 어느 단계에서 선택되었는지 (0=1km, 1=2km, 2=3km) */
        /* 0이면 "근처 대체 장소", 1이면 "2km까지 확장", 2면 "3km까지 확장" 등으로 프론트 표시 가능 */
        int fallbackLevel,
         /* 예비 목록(ScheduleAlt)에서 나온 건지 여부 (현재는 항상 false) */
        boolean fromOverflow,
         /* 실제 장소 정보 (이름, 주소, 사진 등) */
        SchedulePlaceInfo placeInfo
) {}
