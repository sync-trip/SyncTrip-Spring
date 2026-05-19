package com.sync.algorithm.step2;

import com.sync.algorithm.step1.MainPoolPlace;

import java.util.List;

public record Step2Result(
        List<DayGroup> dayGroups,
        List<MainPoolPlace> overflow
) {}
