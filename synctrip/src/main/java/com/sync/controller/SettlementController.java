package com.sync.controller;

import com.sync.common.annotation.LoginUser;
import com.sync.dto.finance.SettlementResponse;
import com.sync.service.NotificationService;
import com.sync.service.SettlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정산 API 컨트롤러
 *
 * 엔드포인트 목록:
 *   GET  /api/bands/{bandId}/settlement          정산 금액 계산 조회
 *   POST /api/bands/{bandId}/settlement/request  밴드 전원에게 정산 요청 알림 발송
 */
@RestController
@RequestMapping("/api/bands/{bandId}/settlement")
public class SettlementController {

    private final SettlementService settlementService;
    private final NotificationService notificationService;

    public SettlementController(SettlementService settlementService,
                                NotificationService notificationService) {
        this.settlementService = settlementService;
        this.notificationService = notificationService;
    }

    /**
     * 정산 금액 계산 조회
     * 밴드 내 지출을 분석해 누가 누구에게 얼마를 보내야 하는지 계산합니다.
     */
    @GetMapping
    public ResponseEntity<SettlementResponse> calculate(
            @LoginUser Long userId,
            @PathVariable Long bandId
    ) {
        return ResponseEntity.ok(settlementService.calculate(userId, bandId));
    }

    /**
     * 정산 요청 알림 발송
     * 밴드 멤버 전원에게 SETTLEMENT_REQUEST 알림을 보냅니다.
     * 정산 결과 화면에서 "정산 요청하기" 버튼 클릭 시 호출합니다.
     */
    @PostMapping("/request")
    public ResponseEntity<Void> requestSettlement(
            @LoginUser Long userId,
            @PathVariable Long bandId
    ) {
        notificationService.requestSettlement(userId, bandId);
        return ResponseEntity.ok().build();
    }
}
