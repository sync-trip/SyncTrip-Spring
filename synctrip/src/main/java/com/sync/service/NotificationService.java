package com.sync.service;

import com.sync.domain.band.Band;
import com.sync.domain.band.BandMember;
import com.sync.domain.notification.Notification;
import com.sync.domain.notification.NotificationType;
import com.sync.domain.user.User;
import com.sync.dto.notification.NotificationResponse;
import com.sync.repository.BandMemberRepository;
import com.sync.repository.BandRepository;
import com.sync.repository.NotificationRepository;
import com.sync.repository.UserRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    public void notify(Long userId, Long bandId, NotificationType type, String content) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;
        Band band = bandId != null ? bandRepository.findById(bandId).orElse(null) : null;
        notificationRepository.save(Notification.create(user, band, type, content));
        fcmService.send(user.getFcmToken(), type.getTitle(), content);
    }

    public void notifyAll(Long bandId, NotificationType type, String content) {
        List<BandMember> members = bandMemberRepository.findByBandId(bandId);
        for (BandMember member : members) {
            notify(member.getUser().getId(), bandId, type, content);
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    public void markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."));
        if (!notification.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 알림만 읽음 처리할 수 있습니다.");
        }
        notification.markRead();
    }

    public void registerFcmToken(Long userId, String token) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        user.updateFcmToken(token);
    }
}
