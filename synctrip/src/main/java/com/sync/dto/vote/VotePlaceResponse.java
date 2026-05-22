package com.sync.dto.vote;

import com.sync.domain.place.PlaceApiSource;
import com.sync.domain.place.PlaceCategory;

public record VotePlaceResponse(
    Long placeId,
    PlaceApiSource apiSource,
    String name,
    PlaceCategory category,
    double latitude,
    double longitude,
    String address,
    Float rating,
    String thumbnailUrl,
    boolean myBookmark,
    Integer myVoteResult  // null=미투표, 1=LIKE, -1=DISLIKE, 0=자동LIKE(내 북마크)
) {}
