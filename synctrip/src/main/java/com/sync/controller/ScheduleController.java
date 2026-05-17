package com.sync.controller;

import com.sync.common.annotation.LoginUser;
import com.sync.dto.schedule.ScheduleAltResponse;
import com.sync.dto.schedule.ScheduleResponse;
import com.sync.service.ScheduleGenerateService;
import com.sync.service.ScheduleService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bands/{bandId}/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final ScheduleGenerateService scheduleGenerateService;

    public ScheduleController(ScheduleService scheduleService,
                               ScheduleGenerateService scheduleGenerateService) {
        this.scheduleService = scheduleService;
        this.scheduleGenerateService = scheduleGenerateService;
    }

    @PostMapping("/generate")
    public ResponseEntity<Void> generateSchedule(
            @LoginUser Long userId,
            @PathVariable Long bandId) {
        scheduleGenerateService.generate(userId, bandId);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<ScheduleResponse> getSchedule(
            @LoginUser Long userId,
            @PathVariable Long bandId) {
        return ResponseEntity.ok(scheduleService.getSchedule(userId, bandId));
    }

    @GetMapping("/alts")
    public ResponseEntity<List<ScheduleAltResponse>> getScheduleAlts(
            @LoginUser Long userId,
            @PathVariable Long bandId) {
        return ResponseEntity.ok(scheduleService.getScheduleAlts(userId, bandId));
    }
}
