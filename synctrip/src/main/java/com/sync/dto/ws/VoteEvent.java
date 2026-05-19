package com.sync.dto.ws;

public record VoteEvent(
    Long userId,
    Long placeId,
    int myVotedCount,
    int totalPlaces
) {}
