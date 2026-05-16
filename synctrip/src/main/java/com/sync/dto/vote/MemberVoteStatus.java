package com.sync.dto.vote;

public record MemberVoteStatus(Long userId, String name, int votedCount, boolean complete) {}
