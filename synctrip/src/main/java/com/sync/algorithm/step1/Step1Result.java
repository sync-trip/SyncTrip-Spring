package com.sync.algorithm.step1;

import java.util.List;

public record Step1Result(
        List<MainPoolPlace> mainPool,
        List<AltPoolPlace> altPool,
        Step1Meta meta
) {}