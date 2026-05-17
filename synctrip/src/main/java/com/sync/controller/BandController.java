package com.sync.controller;

import com.sync.common.annotation.LoginUser;
import com.sync.dto.band.BandCreateRequest;
import com.sync.dto.band.BandJoinRequest;
import com.sync.dto.band.BandInviteCodeResponse;
import com.sync.dto.band.BandMemberResponse;
import com.sync.dto.band.BandReadyResponse;
import com.sync.dto.band.BandResponse;
import com.sync.dto.band.BandStatusTransitionResponse;
import com.sync.dto.band.BandUpdateRequest;
import com.sync.service.BandService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bands")
public class BandController {

    private final BandService bandService;

    public BandController(BandService bandService) {
        this.bandService = bandService;
    }

    @GetMapping
    // 현재 사용자가 포함된 모든 밴드 목록 조회
    public ResponseEntity<List<BandResponse>> getMyBands(
            @LoginUser Long userId
    ) {
        return ResponseEntity.ok(bandService.getMyBands(userId));
    }

    @PostMapping
    // JWT 인증된 사용자가 새로운 밴드(여행)를 생성
    // @LoginUser: JWT의 subject(userId)를 자동으로 주입
    public ResponseEntity<BandResponse> createBand(
            @LoginUser Long userId,
            @RequestBody BandCreateRequest request
    ) {
        return ResponseEntity.ok(bandService.createBand(userId, request));
    }

    @PostMapping("/join")
    public ResponseEntity<BandResponse> joinBand(
            @LoginUser Long userId,
            @RequestBody BandJoinRequest request
    ) {
        return ResponseEntity.ok(bandService.joinBand(userId, request.inviteCode()));
    }

    @PostMapping("/{bandId}/invite-code")
    // 방장이 초대하기 버튼을 누르면, 만료 시 자동 갱신 후 유효한 초대코드를 반환
    public ResponseEntity<BandInviteCodeResponse> getOrRefreshInviteCode(
            @LoginUser Long userId,
            @PathVariable Long bandId
    ) {
        return ResponseEntity.ok(bandService.getOrRefreshInviteCode(userId, bandId));
    }

    @PostMapping("/{bandId}/invite-code/reissue")
    // 호환용: 명시적 재발급 경로도 유지
    public ResponseEntity<BandInviteCodeResponse> reissueInviteCode(
            @LoginUser Long userId,
            @PathVariable Long bandId
    ) {
        return ResponseEntity.ok(bandService.getOrRefreshInviteCode(userId, bandId));
    }

    @GetMapping("/{bandId}/members")
    // 밴드의 멤버 목록 조회 (모든 인증 사용자 접근 가능)
    public ResponseEntity<List<BandMemberResponse>> getBandMembers(@PathVariable Long bandId) {
        return ResponseEntity.ok(bandService.getBandMembers(bandId));
    }

    @PostMapping("/{bandId}/ready")
    public ResponseEntity<BandReadyResponse> markReady(
            @LoginUser Long userId,
            @PathVariable Long bandId
    ) {
        return ResponseEntity.ok(bandService.markReady(userId, bandId));
    }

    @DeleteMapping("/{bandId}/ready")
    public ResponseEntity<BandReadyResponse> markNotReady(
            @LoginUser Long userId,
            @PathVariable Long bandId
    ) {
        return ResponseEntity.ok(bandService.markNotReady(userId, bandId));
    }

    @DeleteMapping("/{bandId}")
    public ResponseEntity<Void> deleteBand(
            @LoginUser Long userId,
            @PathVariable Long bandId
    ) {
        bandService.deleteBand(userId, bandId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{bandId}/update")
    public ResponseEntity<BandResponse> updateBand(
            @LoginUser Long userId,
            @PathVariable Long bandId,
            @RequestBody BandUpdateRequest request
    ) {
        return ResponseEntity.ok(bandService.updateBand(userId, bandId, request));
    }

    @PostMapping("/{bandId}/status/advance")
    public ResponseEntity<BandStatusTransitionResponse> advanceStatus(
            @LoginUser Long userId,
            @PathVariable Long bandId
    ) {
        return ResponseEntity.ok(bandService.advanceBandStatus(userId, bandId));
    }
}


