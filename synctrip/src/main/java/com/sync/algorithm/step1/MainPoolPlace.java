package com.sync.algorithm.step1;

public record MainPoolPlace(
        long placeId,
        double priorityScore,
        double voteScore,
        int likeCount,
        int dislikeCount,
        double distanceKm,
        boolean isOutlierCandidate
) {}