package com.sync.algorithm.step1;

import java.util.List;

public record Step1Input(
        GroupInfo group,
        List<MemberInfo> members,
        List<PlaceInfo> places,
        List<VoteInfo> votes
) {}