package com.sync.controller;

import com.sync.common.annotation.LoginUser;
import com.sync.dto.pick.PlacePickListResponse;
import com.sync.dto.pick.PlacePickRequest;
import com.sync.dto.pick.PlacePickResponse;
import com.sync.service.PlacePickService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개인별 장바구니(Pick) API
 * - 밴드별로 사용자가 담은 장소를 추가/조회/삭제한다.
 */
@RestController
@RequestMapping("/api/bands/{bandId}/picks")
public class PlacePickController {

    private final PlacePickService placePickService;

    public PlacePickController(PlacePickService placePickService) {
        this.placePickService = placePickService;
    }

    @PostMapping
    public ResponseEntity<PlacePickResponse> addPick(
            @LoginUser Long userId,
            @PathVariable Long bandId,
            @RequestBody PlacePickRequest request
    ) {
        return ResponseEntity.ok(placePickService.addPick(userId, bandId, request));
    }

    @GetMapping
    public ResponseEntity<PlacePickListResponse> getMyPicks(
            @LoginUser Long userId,
            @PathVariable Long bandId
    ) {
        return ResponseEntity.ok(placePickService.getMyPicks(userId, bandId));
    }

    @DeleteMapping("/{placeId}")
    public ResponseEntity<Void> removePick(
            @LoginUser Long userId,
            @PathVariable Long bandId,
            @PathVariable Long placeId
    ) {
        placePickService.removePick(userId, bandId, placeId);
        return ResponseEntity.noContent().build();
    }
}

