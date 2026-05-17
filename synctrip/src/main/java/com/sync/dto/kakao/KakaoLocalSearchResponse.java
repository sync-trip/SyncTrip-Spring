package com.sync.dto.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record KakaoLocalSearchResponse(
        List<Document> documents,
        Meta meta
) {
    public record Document(
            String id,
            @JsonProperty("place_name") String placeName,
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("address_name") String addressName,
            @JsonProperty("road_address_name") String roadAddressName,
            String x,
            String y
    ) {}

    public record Meta(
            @JsonProperty("total_count") int totalCount,
            @JsonProperty("is_end") boolean isEnd
    ) {}
}
