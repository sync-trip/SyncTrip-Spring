package com.sync.controller;

import com.sync.common.annotation.LoginUser;
import com.sync.dto.stamp.PassportStampResponse;
import com.sync.dto.user.UserProfileResponse;
import com.sync.dto.user.UserProfileUpdateRequest;
import com.sync.service.PassportStampService;
import com.sync.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final PassportStampService passportStampService;

    public UserController(UserService userService, PassportStampService passportStampService) {
        this.userService = userService;
        this.passportStampService = passportStampService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(@LoginUser Long userId) {
        return ResponseEntity.ok(userService.getMyProfile(userId));
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
            @LoginUser Long userId,
            @Valid @RequestBody UserProfileUpdateRequest request
    ) {
        return ResponseEntity.ok(userService.updateMyProfile(userId, request));
    }

    /** GET /api/users/me/stamps — 내 여권 스탬프 목록 */
    @GetMapping("/me/stamps")
    public ResponseEntity<List<PassportStampResponse>> getMyStamps(@LoginUser Long userId) {
        return ResponseEntity.ok(passportStampService.getMyStamps(userId));
    }
}
