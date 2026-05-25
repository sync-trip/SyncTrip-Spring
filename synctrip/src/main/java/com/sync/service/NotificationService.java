package com.sync.service;

import com.sync.domain.band.Band;
import com.sync.domain.band.BandMember;
import com.sync.domain.notification.Notification;
import com.sync.domain.notification.NotificationType;
import com.sync.domain.user.User;
import com.sync.dto.notification.NotificationResponse;
import com.sync.dto.notification.NotificationSettingResponse;
import com.sync.repository.BandMemberRepository;
import com.sync.repository.BandRepository;
import com.sync.repository.NotificationRepository;
import com.sync.repository.UserRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 알림 서비스
 *
 * 알림은 두 단계로 처리됩니다:
 *   1. DB 저장 → 앱 내 알림 센터에서 목록 조회 가능
 *   2. FCM 발송 → 기기에 즉시 푸시 알림 전달
 *
 * 알림이 발생하는 시점:
 *   - 밴드원 준비완료    → MEMBER_READY  (방장에게 1건)
 *   - 투표 시작         → VOTE_STARTED  (전원에게)
 *   - 일정 생성/수정    → SCHEDULE_UPDATED (전원에게)
 */
@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final BandMemberRepository bandMemberRepository;
    private final UserRepository userRepository;
    private final BandRepository bandRepository;
    private final FcmService fcmService;

    public NotificationService(NotificationRepository notificationRepository,
                               BandMemberRepository bandMemberRepository,
                               UserRepository userRepository,
                               BandRepository bandRepository,
                               FcmService fcmService) {
        this.notificationRepository = notificationRepository;
        this.bandMemberRepository = bandMemberRepository;
        this.userRepository = userRepository;
        this.bandRepository = bandRepository;
        this.fcmService = fcmService;
    }

    /**
     * 특정 유저 1명에게 알림 발송
     * - 탈퇴한 유저는 건너뜀
     * - FCM 토큰이 없으면 DB 저장만 하고 푸시는 생략
     */
    public void notify(Long userId, Long bandId, NotificationType type, String content) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId).orElse(null);
        if (user == null) return;

        Band band = bandId != null ? bandRepository.findById(bandId).orElse(null) : null;
        // 알림 끔 여부와 관계없이 DB에는 항상 저장 (앱 내 알림 센터에서 볼 수 있어야 함)
        notificationRepository.save(Notification.create(user, band, type, content));
        // FCM 푸시는 유저가 해당 타입 알림을 켜둔 경우에만 발송
        if (user.isNotificationEnabled(type)) {
            fcmService.send(user.getFcmToken(), type.getTitle(), content);
        }
    }

    /**
     * 밴드 전원에게 알림 발송
     * - JOIN FETCH로 BandMember + User를 한 번에 조회하여 N+1 방지
     * - 탈퇴한 멤버는 건너뜀
     */
    public void notifyAll(Long bandId, NotificationType type, String content) {
        Band band = bandRepository.findById(bandId).orElse(null);
        // findByBandIdWithUser: JOIN FETCH로 user를 미리 로딩 (N+1 방지)
        List<BandMember> members = bandMemberRepository.findByBandIdWithUser(bandId);
        for (BandMember member : members) {
            User user = member.getUser();
            if (user.isDeleted()) continue;
            notificationRepository.save(Notification.create(user, band, type, content));
            if (user.isNotificationEnabled(type)) {
                fcmService.send(user.getFcmToken(), type.getTitle(), content);
            }
        }
    }

    /**
     * 밴드 멤버 중 특정 유저를 제외하고 알림 발송
     * - VOTE_STARTED 시 방장 제외에 사용
     */
    public void notifyAllExcept(Long bandId, Long excludeUserId, NotificationType type, String content) {
        Band band = bandRepository.findById(bandId).orElse(null);
        List<BandMember> members = bandMemberRepository.findByBandIdWithUser(bandId);
        for (BandMember member : members) {
            User user = member.getUser();
            if (user.isDeleted()) continue;
            if (user.getId().equals(excludeUserId)) continue;
            notificationRepository.save(Notification.create(user, band, type, content));
            if (user.isNotificationEnabled(type)) {
                fcmService.send(user.getFcmToken(), type.getTitle(), content);
            }
        }
    }

    /**
     * 내 알림 목록 조회 (최신순, 페이지네이션)
     * - 기본 20건씩 반환. 클라이언트가 page=0, size=20 으로 요청.
     * - 응답 목록 크기가 size보다 작으면 마지막 페이지.
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(Long userId, int page, int size) {
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .stream()
                .map(NotificationResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 미읽음 알림 개수 조회
     * - 앱 아이콘의 뱃지 숫자에 사용
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    /**
     * 알림 1건 읽음 처리
     * - 본인 알림만 읽음 처리 가능
     */
    public void markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."));
        if (!notification.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 알림만 읽음 처리할 수 있습니다.");
        }
        notification.markRead();
    }

    /**
     * 전체 읽음 처리
     * - 미읽음 알림을 한 번에 모두 읽음으로 변경
     * - 반환값: 읽음 처리된 알림 개수
     */
    public int markAllRead(Long userId) {
        return notificationRepository.markAllReadByUserId(userId);
    }

    /**
     * FCM 디바이스 토큰 등록
     * - 앱 실행 시 Firebase SDK가 발급한 토큰을 서버에 저장
     * - 이후 notify() 호출 시 이 토큰으로 푸시 발송
     */
    public void registerFcmToken(Long userId, String token) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        user.updateFcmToken(token);
    }

    /**
     * 알림 수신 설정 조회
     * - 현재 알림 타입별 on/off 상태를 반환
     * - 프론트 알림 설정 화면 진입 시 초기값 로딩에 사용
     */
    @Transactional(readOnly = true)
    public NotificationSettingResponse getNotificationSettings(Long userId) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        return NotificationSettingResponse.from(user);
    }

    /**
     * 알림 수신 설정 변경
     * - 특정 알림 타입의 FCM 푸시 수신 여부를 on/off
     * - DB 저장(In-App 알림)은 항상 되며, FCM 푸시만 제어됨
     */
    public void updateNotificationSetting(Long userId, NotificationType type, boolean enabled) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        user.updateNotificationSetting(type, enabled);
    }

    /**
     * 알림 1건 삭제
     * - 본인 알림만 삭제 가능 (쿼리에서 user_id 조건으로 검증)
     */
    public void deleteNotification(Long userId, Long notificationId) {
        int deleted = notificationRepository.deleteByIdAndUserId(notificationId, userId);
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "알림을 찾을 수 없거나 삭제 권한이 없습니다.");
        }
    }

    /**
     * 알림 전체 삭제
     * - 내 알림을 모두 삭제 (읽은 것, 안 읽은 것 모두)
     */
    public void deleteAllNotifications(Long userId) {
        notificationRepository.deleteAllByUserId(userId);
    }

    /**
     * 정산 요청 알림 발송
     * - 밴드 멤버가 정산 요청 시 밴드 전원에게 알림
     * - 정산 결과 화면에서 "정산 요청하기" 버튼 클릭 시 호출
     */
    public void requestSettlement(Long userId, Long bandId) {
        if (!bandMemberRepository.existsByBandIdAndUserId(bandId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "밴드 멤버만 정산 요청을 보낼 수 있습니다.");
        }
        Band band = bandRepository.findById(bandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "밴드를 찾을 수 없습니다."));
        User requester = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        notifyAll(bandId, NotificationType.SETTLEMENT_REQUEST,
                requester.getName() + "님이 " + band.getName() + " 정산을 요청했어요! 확인해보세요 💰");
    }
}
