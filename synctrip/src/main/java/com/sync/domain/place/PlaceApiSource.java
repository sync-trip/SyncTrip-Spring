package com.sync.domain.place;

/**
 * 장소 데이터가 어디서 왔는지 나타내는 제공자 타입
 * - KAKAO: 국내 장소 검색/상세 정보
 * - GOOGLE: 해외 장소 검색/상세 정보
 */
public enum PlaceApiSource {
    KAKAO,
    GOOGLE
}

