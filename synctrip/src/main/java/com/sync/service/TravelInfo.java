package com.sync.service;

/**
 * 두 좌표 간 이동 정보 — 이동시간(분) + 대중교통 노선 요약.
 *
 * @param minutes        이동 시간 (분)
 * @param transitSummary 이용 노선 요약 (예: "丸ノ内線 → 日比谷線", "도보"). API 실패 시 null.
 */
public record TravelInfo(int minutes, String transitSummary) {

    /** API 실패 시 haversine 시간만으로 생성 */
    public static TravelInfo fallback(int minutes) {
        return new TravelInfo(minutes, null);
    }
}
