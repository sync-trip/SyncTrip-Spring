package com.sync.algorithm;

public final class AlgorithmConstants {
    private AlgorithmConstants() {}

    public static final double EARTH_RADIUS_KM       = 6371.0;
    public static final double DIST_PENALTY_FLOOR_KM = 3.0;
    public static final double OUTLIER_DIST_KM       = 30.0;

    public static final int    RELAXED_DENSITY       = 5;
    public static final int    PACKED_DENSITY        = 8;
    public static final int    RELAXED_FOOD_PER_DAY  = 1;
    public static final int    PACKED_FOOD_PER_DAY   = 2;

    public static final double VOTE_SCORE_WEIGHT     = 0.7;
    public static final double DIST_PENALTY_WEIGHT   = 0.3;
}