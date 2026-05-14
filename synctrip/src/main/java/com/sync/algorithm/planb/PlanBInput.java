package com.sync.algorithm.planb;

import com.sync.algorithm.step1.AltPoolPlace;
import com.sync.algorithm.step1.MainPoolPlace;
import com.sync.algorithm.step1.PlaceInfo;
import com.sync.algorithm.step3.Step3Result;

import java.util.List;

public record PlanBInput(
        Step3Result step3Result,
        List<AltPoolPlace> altPool,    // Step1Result.altPool()
        List<PlaceInfo> places,
        long targetPlaceId             // 교체할 장소 ID
) {}
