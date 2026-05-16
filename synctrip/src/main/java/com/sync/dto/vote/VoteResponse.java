package com.sync.dto.vote;

import java.time.LocalDateTime;

public record VoteResponse(Long voteId, Long placeId, int result, LocalDateTime votedAt) {}
