package com.sync.dto.vote;

import com.sync.domain.place.PlaceCategory;

/** 투표 결과 화면 — 장소별 좋아요/싫어요 집계 및 통과 여부 */
public record VotePlaceResultResponse(
    Long placeId,
    String name,
    PlaceCategory category,
    String thumbnailUrl,
    String address,
    double latitude,
    double longitude,
    int likeCount,       // result >= 0 (1=LIKE, 0=자동LIKE) 합산
    int dislikeCount,    // result == -1 합산
    boolean passed,      // likeCount >= ceil(투표 자격자 수 × 0.5)
    Integer myVoteResult // null=미투표, 1=LIKE, -1=DISLIKE, 0=자동LIKE
) {}
