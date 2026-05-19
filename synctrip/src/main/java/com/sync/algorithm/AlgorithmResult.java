package com.sync.algorithm;

import com.sync.algorithm.step1.Step1Result;
import com.sync.algorithm.step3.Step3Result;

public record AlgorithmResult(
        Step3Result step3Result,
        Step1Result step1Result   // altPool 보존 — PlanB 호출 시 사용
) {}
