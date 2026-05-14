package com.sync.dto.pick;

import java.util.List;

/**
 * 특정 밴드/사용자 기준 장바구니 전체 목록 응답 DTO
 * - currentCount로 현재 담긴 개수를 보여주고
 * - maxCount로 제한값을 같이 내려준다.
 */
public record PlacePickListResponse(
    int currentCount,
    int maxCount,
    List<PlacePickResponse> items
) {
}

