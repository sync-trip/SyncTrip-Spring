package com.sync.dto.vote;

public record VoteStatusResponse(
    int totalPlaces,
    int myVotedCount,
    boolean myComplete
) {}
