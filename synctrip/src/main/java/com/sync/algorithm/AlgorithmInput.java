package com.sync.algorithm;

import com.sync.algorithm.step1.GroupInfo;
import com.sync.algorithm.step1.MemberInfo;
import com.sync.algorithm.step1.PlaceInfo;
import com.sync.algorithm.step1.VoteInfo;
import com.sync.algorithm.step3.OpeningHours;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public record AlgorithmInput(
        GroupInfo group,
        List<MemberInfo> members,
        List<PlaceInfo> places,
        List<VoteInfo> votes,
        LocalTime dayStartTime,                   // null → SimpleTsp.DEFAULT_DAY_START
        Map<Long, OpeningHours> openingHoursById  // 해외용, 국내는 Map.of()
) {}
