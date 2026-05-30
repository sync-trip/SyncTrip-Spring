package com.sync.algorithm;

import java.time.LocalTime;

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

    public static final int    KMEANS_MAX_ITER       = 100;

    /** 이동 시간 추정용 고정 속도 (도심 도보/대중교통 평균) */
    public static final double TRAVEL_SPEED_KMH     = 25.0;

    /** 이동 시간 하한 — 같은 건물 등 0km 구간에도 최소 N분 적용 [작업4] */
    public static final int    MIN_TRAVEL_MINUTES   = 3;

    public static final int    MAX_PLANB_RECOMMENDATIONS = 7;
    public static final double PLANB_VOTE_WEIGHT         = 0.6;
    public static final double PLANB_GEO_WEIGHT          = 0.4;
    public static final double PLANB_MAX_DIST_KM         = 1.0;

    /** LATE_SCHEDULE 경고 기준 시각 (22:00 이후 시작) [작업2] */
    public static final LocalTime LATE_WARN_TIME    = LocalTime.of(22, 0);
}
