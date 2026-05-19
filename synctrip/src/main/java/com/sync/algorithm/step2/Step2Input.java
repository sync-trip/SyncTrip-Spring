package com.sync.algorithm.step2;

import com.sync.algorithm.step1.PlaceInfo;
import com.sync.algorithm.step1.Step1Result;

import java.util.List;

public record Step2Input(
        Step1Result step1Result,
        List<PlaceInfo> places
) {}
