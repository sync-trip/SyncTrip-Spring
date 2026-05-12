package com.sync.algorithm.step1;

public record Step1Meta(
        int K,
        int totalMembers,
        int passedThreshold,
        int densityLimit,
        int foodPerDayQuota,
        double maxDistKm,
        double destinationLat,
        double destinationLng
) {}