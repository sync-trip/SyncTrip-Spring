package com.sync.dto.vote;

import java.util.List;

public record GroupVoteStatusResponse(
    int totalPlaces,
    int totalVotingMembers,
    List<MemberVoteStatus> memberStatuses
) {}
