package com.sync.algorithm.step3;

import java.time.LocalTime;

/** 해외 장소의 영업시간 (국내는 NULL — 체크 안 함) */
public record OpeningHours(LocalTime open, LocalTime close) {}
