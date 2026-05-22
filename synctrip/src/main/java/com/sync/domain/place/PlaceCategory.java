package com.sync.domain.place;

/**
 * SyncTrip에서 사용하는 장소 카테고리
 * - DDL의 ENUM과 1:1로 맞춰서 저장한다.
 * - densityPoint는 일정 최적화 시 가중치 계산에 사용된다.
 */
public enum PlaceCategory {
    FOOD(0),
    CULTURE(2),
    ACTIVITY(3),
    SHOPPING(1),
    NATURE(2),
    ETC(1),
    LODGING(1);

    private final int densityPoint;

    PlaceCategory(int densityPoint) {
        this.densityPoint = densityPoint;
    }

    public int densityPoint() {
        return densityPoint;
    }
}

