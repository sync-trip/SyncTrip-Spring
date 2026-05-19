package com.sync.algorithm.step3;

import com.sync.algorithm.step1.MainPoolPlace;

import java.util.List;

public record Step3Result(
        List<DaySchedule> daySchedules,
        List<MainPoolPlace> overflow  // Step2 overflow 전달 — PlanB 입력용
) {}
