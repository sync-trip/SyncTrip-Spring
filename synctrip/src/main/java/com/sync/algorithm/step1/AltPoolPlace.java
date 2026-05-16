package com.sync.algorithm.step1;

public record AltPoolPlace(
        long placeId,
        double priorityScore,
        double voteScore,
        int likeCount,
        int altRank
) {}