package com.sync.controller;

import com.sync.domain.notification.NotificationType;
import com.sync.domain.user.OauthProvider;
import com.sync.domain.user.User;
import com.sync.dto.notification.NotificationResponse;
import com.sync.dto.schedule.PlanBResponse;
import com.sync.repository.UserRepository;
import com.sync.service.NotificationService;
import com.sync.service.ScheduleService;
import com.sync.service.jwt.JwtTokenProvider;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개발 환경 전용 디버그 엔드포인트
 *
 * 활성화 조건: app.security.enabled=false (개발 모드)
 *
 * 용도:
 * - 토큰 없이 API 테스트
 * - 테스트 사용자 생성
 * - 테스트용 JWT 토큰 발급
 *
 * 운영 환경에서는 이 클래스가 로드되지 않음
 */
@RestController
@RequestMapping("/debug")
@ConditionalOnProperty(
    name = "app.security.enabled",
    havingValue = "false",
    matchIfMissing = true  // 기본값: 개발 모드
)
public class DebugController {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final ScheduleService scheduleService;
    private final NotificationService notificationService;

    public DebugController(JwtTokenProvider jwtTokenProvider,
                           UserRepository userRepository,
                           ScheduleService scheduleService,
                           NotificationService notificationService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.scheduleService = scheduleService;
        this.notificationService = notificationService;
    }

    /**
     * 상태 확인: 개발 모드인지 확인
     *
     * curl http://localhost:8080/debug/status
     */
    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(new StatusResponse(
            true,
            "개발 모드 활성화됨, 모든 API가 인증 없이 접근 가능합니다"
        ));
    }

    /**
     * 테스트 사용자 생성 또는 조회
     *
     * curl -X POST http://localhost:8080/debug/user?name=testuser
     */
    @PostMapping("/user")
    public ResponseEntity<?> createOrGetTestUser(
        @RequestParam(defaultValue = "테스트사용자") String name,
        @RequestParam(defaultValue = "test@example.com") String email,
        @RequestParam(defaultValue = "testuser_123") String oauthId
    ) {
        // 기존 사용자 조회
        var user = userRepository.findByOauthProviderAndOauthIdAndIsDeletedFalse(
            OauthProvider.KAKAO, oauthId
        );

        if (user.isPresent()) {
            return ResponseEntity.ok(new UserResponse(
                user.get().getId(),
                user.get().getName(),
                user.get().getEmail(),
                "(기존 사용자)"
            ));
        }

        // 새 사용자 생성
        User newUser = User.kakaoUser(email, name, null, oauthId);
        userRepository.save(newUser);

        return ResponseEntity.created(null).body(new UserResponse(
            newUser.getId(),
            newUser.getName(),
            newUser.getEmail(),
            "(신규 생성)"
        ));
    }

    /**
     * 테스트용 JWT 토큰 발급
     *
     * curl -X POST http://localhost:8080/debug/token?userId=1
     */
    @PostMapping("/token")
    public ResponseEntity<?> getTestToken(
        @RequestParam(defaultValue = "1") Long userId
    ) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
            .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: userId=" + userId));

        JwtTokenProvider.TokenPair tokenPair = jwtTokenProvider.issueTokenPair(user);

        return ResponseEntity.ok(new TokenResponse(
            user.getId(),
            user.getName(),
            tokenPair.accessToken(),
            tokenPair.refreshToken(),
            tokenPair.accessTokenExpiresIn(),
            tokenPair.refreshTokenExpiresIn(),
            "사용: Authorization: Bearer {accessToken}"
        ));
    }

    /**
     * Plan B 추천 디버그 조회
     *
     * 목적: Plan B (대체 장소 추천) 로직 동작을 디버그하기 위한 엔드포인트
     * 개발 모드 전용 (app.security.enabled=false 일때만 활성화)
     *
     * 반환 데이터:
     * - recommendations: 최종 추천 리스트 (최대 3개)
     *   각 항목에 searchRadiusKmUsed, fallbackLevel 포함
     * - fallbackStageCounts: 각 단계별 추천 개수
     *   { 0: 2개, 1: 1개 } 이면 "1km 단계에서 2개, 2km 단계에서 1개"
     *
     * 사용 예시:
     * curl "http://localhost:8080/debug/plan-b?userId=1&bandId=10&targetPlaceId=100"
     *
     * 응답 예시:
     * {
     *   "userId": 1,
     *   "bandId": 10,
     *   "targetPlaceId": 100,
     *   "recommendationCount": 3,
     *   "fallbackStageCounts": {
     *     "0": 2,
     *     "1": 1
     *   },
     *   "recommendations": [
     *     {
     *       "placeId": 200,
     *       "searchRadiusKmUsed": 1.0,
     *       "fallbackLevel": 0,
     *       "distanceKmToTarget": 0.8,
     *       "recommendScore": 0.82,
     *       ...
     *     }
     *   ]
     * }
     */
    /**
     * 특정 유저에게 알림 1건 발송
     *
     * curl -X POST "http://localhost:8080/debug/notify?userId=1&bandId=10&type=VOTE_STARTED&content=테스트"
     */
    @PostMapping("/notify")
    public ResponseEntity<?> sendNotification(
            @RequestParam Long userId,
            @RequestParam(required = false) Long bandId,
            @RequestParam(defaultValue = "VOTE_STARTED") NotificationType type,
            @RequestParam(defaultValue = "테스트 알림입니다.") String content
    ) {
        notificationService.notify(userId, bandId, type, content);
        List<NotificationResponse> notifications = notificationService.getNotifications(userId, 0, 20);
        return ResponseEntity.ok(Map.of(
                "sent", true,
                "userId", userId,
                "type", type,
                "content", content,
                "recentNotifications", notifications
        ));
    }

    /**
     * 밴드 전원에게 알림 발송
     *
     * curl -X POST "http://localhost:8080/debug/notify-all?bandId=10&type=SCHEDULE_UPDATED&content=일정확인"
     */
    @PostMapping("/notify-all")
    public ResponseEntity<?> sendNotificationAll(
            @RequestParam Long bandId,
            @RequestParam(defaultValue = "SCHEDULE_UPDATED") NotificationType type,
            @RequestParam(defaultValue = "테스트 단체 알림입니다.") String content
    ) {
        notificationService.notifyAll(bandId, type, content);
        return ResponseEntity.ok(Map.of(
                "sent", true,
                "bandId", bandId,
                "type", type,
                "content", content
        ));
    }

    @GetMapping("/plan-b")
    public ResponseEntity<?> debugPlanB(
        @RequestParam Long userId,
        @RequestParam Long bandId,
        @RequestParam Long targetPlaceId
    ) {
        /* 1. Plan B 추천 로직 실행 */
        List<PlanBResponse> recommendations = scheduleService.getPlanBRecommendations(userId, bandId, targetPlaceId);

        /* 2. fallbackLevel별로 그룹화하여 각 단계에서 몇 개씩 추천받았는지 집계
           0: 1km 단계, 1: 2km 단계, 2: 3km 단계 */
        Map<Integer, Long> fallbackStageCounts = recommendations.stream()
                .collect(Collectors.groupingBy(PlanBResponse::fallbackLevel, Collectors.counting()));

        /* 3. 디버그 정보 포함한 응답 반환 */
        return ResponseEntity.ok(new PlanBDebugResponse(
                userId,
                bandId,
                targetPlaceId,
                /* 실제 추천 개수 (최대 3개) */
                recommendations.size(),
                /* 단계별 추천 개수 분포 */
                fallbackStageCounts,
                /* 상세 추천 정보 */
                recommendations
        ));
    }

    // ============ DTO ============

    record StatusResponse(
        boolean success,
        String message
    ) {}

    record UserResponse(
        Long userId,
        String name,
        String email,
        String note
    ) {}

    record TokenResponse(
        Long userId,
        String name,
        String accessToken,
        String refreshToken,
        long accessTokenExpiresIn,
        long refreshTokenExpiresIn,
        String usage
    ) {}

    record PlanBDebugResponse(
        Long userId,
        Long bandId,
        Long targetPlaceId,
        /* 실제 반환된 추천 개수 (0~3) */
        int recommendationCount,
        /* 단계별 분포: { 0: X개, 1: Y개, 2: Z개 }
           0 = 1km 반경에서 X개
           1 = 2km 반경에서 Y개
           2 = 3km 반경에서 Z개 */
        Map<Integer, Long> fallbackStageCounts,
        /* 최종 추천 리스트 (각 항목의 fallbackLevel과 searchRadiusKmUsed 포함) */
        List<PlanBResponse> recommendations
    ) {}
}

