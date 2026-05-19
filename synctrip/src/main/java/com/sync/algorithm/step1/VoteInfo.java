package com.sync.algorithm.step1;

/** result: 1=LIKE, 0=BOOKMARK(본인 자동 LIKE), -1=DISLIKE */
public record VoteInfo(long placeId, long userId, int result) {}