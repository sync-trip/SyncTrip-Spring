package com.sync.service;

import com.sync.domain.band.Band;
import com.sync.domain.band.BandMember;
import com.sync.domain.band.BandRole;
import com.sync.domain.band.TravelStyle;
import com.sync.domain.notification.Notification;
import com.sync.domain.notification.NotificationType;
import com.sync.domain.user.User;
import com.sync.dto.notification.NotificationResponse;
import com.sync.repository.BandMemberRepository;
import com.sync.repository.BandRepository;
import com.sync.repository.NotificationRepository;
import com.sync.repository.UserRepository;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private BandMemberRepository bandMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private BandRepository bandRepository;
    @Mock private FcmService fcmService;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository, bandMemberRepository,
                userRepository, bandRepository, fcmService
        );
    }

    @Test
    void notify_savesNotificationAndSendsFcm() {
        User user = makeUser(1L, "A");
        user.updateFcmToken("test-fcm-token");
        Band band = makeBand(10L, user);

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandRepository.findById(10L)).thenReturn(Optional.of(band));
        when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        notificationService.notify(1L, 10L, NotificationType.VOTE_STARTED, "투표 시작!");

        verify(notificationRepository).save(any(Notification.class));
        verify(fcmService).send("test-fcm-token", "투표 시작", "투표 시작!");
    }

    @Test
    void notify_skipsIfUserNotFound() {
        when(userRepository.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.empty());

        notificationService.notify(99L, null, NotificationType.VOTE_STARTED, "내용");

        verify(notificationRepository, never()).save(any());
        verify(fcmService, never()).send(any(), any(), any());
    }

    @Test
    void notify_skipsFcmWhenTokenIsNull() {
        User user = makeUser(1L, "A"); // fcmToken = null

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandRepository.findById(10L)).thenReturn(Optional.empty());
        when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        notificationService.notify(1L, 10L, NotificationType.MEMBER_READY, "준비완료");

        verify(notificationRepository).save(any());
        verify(fcmService).send(null, "멤버 준비완료", "준비완료");
    }

    @Test
    void notifyAll_notifiesEveryBandMember() {
        User userA = makeUser(1L, "A");
        User userB = makeUser(2L, "B");
        Band band = makeBand(10L, userA);

        BandMember mA = BandMember.create(userA, band, BandRole.OWNER);
        BandMember mB = BandMember.create(userB, band, BandRole.MEMBER);

        when(bandRepository.findById(10L)).thenReturn(Optional.of(band));
        when(bandMemberRepository.findByBandIdWithUser(10L)).thenReturn(List.of(mA, mB));
        when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        notificationService.notifyAll(10L, NotificationType.VOTE_STARTED, "투표 시작!");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).hasSize(2);
    }

    @Test
    void getNotifications_returnsOrderedList() {
        User user = makeUser(1L, "A");
        Band band = makeBand(10L, user);

        Notification n1 = Notification.create(user, band, NotificationType.VOTE_STARTED, "첫 번째");
        Notification n2 = Notification.create(user, band, NotificationType.MEMBER_READY, "두 번째");
        setId(n1, 1L);
        setId(n2, 2L);

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(1L), any())).thenReturn(List.of(n2, n1));

        List<NotificationResponse> result = notificationService.getNotifications(1L, 0, 20);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).type()).isEqualTo(NotificationType.MEMBER_READY);
        assertThat(result.get(1).type()).isEqualTo(NotificationType.VOTE_STARTED);
    }

    @Test
    void getUnreadCount_returnsCorrectCount() {
        when(notificationRepository.countUnreadByUserId(1L)).thenReturn(3L);

        assertThat(notificationService.getUnreadCount(1L)).isEqualTo(3L);
    }

    @Test
    void markRead_marksNotificationAsRead() {
        User user = makeUser(1L, "A");
        Band band = makeBand(10L, user);
        Notification notification = Notification.create(user, band, NotificationType.VOTE_STARTED, "내용");
        setId(notification, 5L);

        when(notificationRepository.findById(5L)).thenReturn(Optional.of(notification));

        notificationService.markRead(1L, 5L);

        assertThat(notification.isRead()).isTrue();
    }

    @Test
    void markRead_forbidden_whenOtherUser() {
        User owner = makeUser(1L, "A");
        User other = makeUser(2L, "B");
        Band band = makeBand(10L, owner);
        Notification notification = Notification.create(owner, band, NotificationType.VOTE_STARTED, "내용");
        setId(notification, 5L);

        when(notificationRepository.findById(5L)).thenReturn(Optional.of(notification));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> notificationService.markRead(2L, 5L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void registerFcmToken_updatesUserToken() {
        User user = makeUser(1L, "A");
        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));

        notificationService.registerFcmToken(1L, "new-token-xyz");

        assertThat(user.getFcmToken()).isEqualTo("new-token-xyz");
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    private User makeUser(Long id, String name) {
        User u = User.kakaoUser(name + "@test.com", name, null, id.toString());
        setId(u, id);
        return u;
    }

    private Band makeBand(Long id, User owner) {
        Band b = Band.create(owner, "테스트밴드", "제주도",
                33.4, 126.5, "KR", false,
                LocalDate.now(), LocalDate.now().plusDays(3),
                TravelStyle.PACKED, null, null, null);
        setId(b, id);
        return b;
    }

    private void setId(Object target, Long id) {
        try {
            Field field = findField(target.getClass(), "id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) return findField(clazz.getSuperclass(), name);
            throw e;
        }
    }
}
