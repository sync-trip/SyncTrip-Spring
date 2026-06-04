package com.sync.dto.google;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Google Routes API(신형) 응답 DTO
 * POST https://routes.googleapis.com/directions/v2:computeRoutes (travelMode=TRANSIT)
 *
 * Routes API REST JSON은 camelCase를 사용하므로 @JsonProperty 매핑이 필요 없다.
 * 응답에서 실제로 읽는 필드만 정의하고, 나머지는 FieldMask로 받지 않으므로 무시한다.
 * duration은 "1860s" 형태의 문자열(초 + 's' 접미사)로 내려온다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoutesResponse(
        List<Route> routes
) {
    /** 경로 단위 — duration은 "초s" 문자열(예: "1860s") */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Route(String duration, List<Leg> legs) {}

    /** 구간(leg) 단위 — 도보/대중교통 step 목록 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Leg(List<Step> steps) {}

    /** 이동 step — travelMode는 "TRANSIT" / "WALK" 등 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Step(String travelMode, TransitDetails transitDetails) {}

    /** 대중교통 step에만 존재 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TransitDetails(TransitLine transitLine) {}

    /** 노선 정보 — nameShort 우선(예: "丸ノ内線"), 없으면 name */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TransitLine(String name, String nameShort) {}
}
