package com.sync.algorithm.step2;

import java.util.List;

public record DayGroup(
        int day,
        List<AssignedPlace> places
) {}
