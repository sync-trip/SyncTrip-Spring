package com.sync.domain.band;

public enum BandStatus {
    PLANNING,
    VOTING,
    GENERATING,
    TRAVELLING,
    DONE;

    public BandStatus next() {
        return switch (this) {
            case PLANNING -> VOTING;
            case VOTING -> GENERATING;
            case GENERATING -> TRAVELLING;
            case TRAVELLING -> DONE;
            case DONE -> null;
        };
    }
}
