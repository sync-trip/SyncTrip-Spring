package com.sync.dto.schedule;

import java.time.LocalDate;
import java.util.List;

public record ScheduleResponse(
        Long bandId,
        LocalDate startDate,
        LocalDate endDate,
        List<ScheduleDayResponse> days,
        Long editingUserId,      // 현재 편집 락 보유자 ID (null=미사용 또는 5분 만료)
        String editingUserName,  // 편집 중인 사용자 이름 (null이면 편집자 없음)
        boolean canEdit          // 현재 요청 사용자가 편집 가능한지 여부
) {}
