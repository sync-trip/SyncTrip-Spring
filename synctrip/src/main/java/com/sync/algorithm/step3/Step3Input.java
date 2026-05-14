package com.sync.algorithm.step3;

import com.sync.algorithm.step2.Step2Result;

import java.time.LocalTime;
import java.util.Map;

public record Step3Input(
        Step2Result step2Result,
        boolean isOverseas,
        LocalTime dayStartTime,
        Map<Long, OpeningHours> openingHoursById
) {}
