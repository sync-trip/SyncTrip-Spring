package com.sync.controller;

import com.sync.common.annotation.LoginUser;
import com.sync.dto.schedule.ScheduleAddRequest;
import com.sync.dto.schedule.ScheduleAltResponse;
import com.sync.dto.schedule.ScheduleMoveRequest;
import com.sync.dto.schedule.ScheduleReorderRequest;
import com.sync.dto.schedule.ScheduleResponse;
import com.sync.service.ScheduleService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bands/{bandId}/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @PostMapping("/generate")
    public ResponseEntity<Void> generateSchedule(
            @LoginUser Long userId,
            @PathVariable Long bandId) {
        scheduleService.generateManual(userId, bandId);
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

    @PostMapping("/plan-b")
    public ResponseEntity<List<com.sync.dto.schedule.PlanBResponse>> getPlanBRecommendations(
            @LoginUser Long userId,
            @PathVariable Long bandId,
            @RequestBody com.sync.dto.schedule.PlanBRequest request) {
        return ResponseEntity.ok(scheduleService.getPlanBRecommendations(userId, bandId, request.targetPlaceId()));
    }

    @PatchMapping("/reorder")
    public ResponseEntity<Void> reorderSchedule(
            @LoginUser Long userId,
            @PathVariable Long bandId,
            @RequestBody ScheduleReorderRequest request) {
        scheduleService.reorderSchedule(userId, bandId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/swap")
    public ResponseEntity<Void> swapSchedulePlace(
            @LoginUser Long userId,
            @PathVariable Long bandId,
            @RequestBody com.sync.dto.schedule.ScheduleSwapRequest request) {
        scheduleService.swapSchedulePlace(userId, bandId, request.scheduleId(), request.newPlaceId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/move")
    public ResponseEntity<Void> moveSchedule(
            @LoginUser Long userId,
            @PathVariable Long bandId,
            @RequestBody ScheduleMoveRequest request) {
        scheduleService.moveSchedule(userId, bandId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/add")
    public ResponseEntity<Void> addToSchedule(
            @LoginUser Long userId,
            @PathVariable Long bandId,
            @RequestBody ScheduleAddRequest request) {
        scheduleService.addSlotFromAltPool(userId, bandId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/edit/start")
    public ResponseEntity<Void> startEditing(
            @LoginUser Long userId,
            @PathVariable Long bandId) {
        scheduleService.startEditing(userId, bandId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/edit/finish")
    public ResponseEntity<Void> finishEditing(
            @LoginUser Long userId,
            @PathVariable Long bandId) {
        scheduleService.finishEditing(userId, bandId);
        return ResponseEntity.ok().build();
    }
}
