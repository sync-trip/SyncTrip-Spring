package com.sync.service;

import com.sync.domain.band.Band;
import com.sync.domain.user.User;
import com.sync.config.BandInviteProperties;
import com.sync.dto.band.BandInviteCodeResponse;
import com.sync.repository.BandMemberRepository;
import com.sync.repository.BandRepository;
import com.sync.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BandServiceTest {

    @Mock
    private BandRepository bandRepository;

    @Mock
    private BandMemberRepository bandMemberRepository;

    @Mock
    private UserRepository userRepository;

    private BandService bandService;

    @BeforeEach
    void setUp() {
        // 각 테스트마다 동일한 초대 링크 베이스를 쓰도록 서비스 객체를 직접 생성한다.
        bandService = new BandService(
                bandRepository,
                bandMemberRepository,
                userRepository,
                new BandInviteProperties(
                        "https://test.sync-trip.app/invite?code=",
                        "synctrip://band/join?code="
                )
        );
    }

    @Test
    void getOrRefreshInviteCode_keepsExistingCodeWhenStillValid() {
        User owner = User.kakaoUser("owner@example.com", "owner", null, "100");
        setId(owner, 1L);

        Band band = Band.create(
                owner,
                "봄여행",
                "제주도",
                33.4996,
                126.5312,
                "KR",
                false,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 5)
        );
        setId(band, 10L);

        String originalInviteCode = band.getInviteCode();
        LocalDateTime originalExpiredAt = LocalDateTime.now().plusDays(1);
        setInviteCodeExpiredAt(band, originalExpiredAt);

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(owner));
        when(bandRepository.findById(10L)).thenReturn(Optional.of(band));
        when(bandRepository.save(any(Band.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BandInviteCodeResponse response = bandService.getOrRefreshInviteCode(1L, 10L);

        assertThat(response.bandId()).isEqualTo(10L);
        assertThat(response.inviteCode()).isEqualTo(originalInviteCode);
        assertThat(response.inviteCodeExpiredAt()).isEqualTo(originalExpiredAt);
        assertThat(response.inviteShareLink()).isEqualTo("https://test.sync-trip.app/invite?code=" + originalInviteCode);
        assertThat(response.inviteDeepLink()).isEqualTo("synctrip://band/join?code=" + originalInviteCode);
        verify(bandRepository).save(band);
    }

    @Test
    void getOrRefreshInviteCode_refreshesCodeWhenExpired() {
        User owner = User.kakaoUser("owner@example.com", "owner", null, "100");
        setId(owner, 1L);

        Band band = Band.create(
                owner,
                "봄여행",
                "제주도",
                33.4996,
                126.5312,
                "KR",
                false,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 5)
        );
        setId(band, 10L);

        String originalInviteCode = band.getInviteCode();
        LocalDateTime expiredAt = LocalDateTime.now().minusHours(1);
        setInviteCodeExpiredAt(band, expiredAt);

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(owner));
        when(bandRepository.findById(10L)).thenReturn(Optional.of(band));
        when(bandRepository.save(any(Band.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BandInviteCodeResponse response = bandService.getOrRefreshInviteCode(1L, 10L);

        assertThat(response.bandId()).isEqualTo(10L);
        assertThat(response.inviteCode()).isNotEqualTo(originalInviteCode);
        assertThat(response.inviteCodeExpiredAt()).isAfter(LocalDateTime.now());
        assertThat(response.inviteShareLink()).isEqualTo("https://test.sync-trip.app/invite?code=" + response.inviteCode());
        assertThat(response.inviteDeepLink()).isEqualTo("synctrip://band/join?code=" + response.inviteCode());
        verify(bandRepository).save(band);
    }

    @Test
    void getOrRefreshInviteCode_rejectsNonOwner() {
        User owner = User.kakaoUser("owner@example.com", "owner", null, "100");
        setId(owner, 1L);

        User otherUser = User.kakaoUser("other@example.com", "other", null, "200");
        setId(otherUser, 2L);

        Band band = Band.create(
                owner,
                "봄여행",
                "제주도",
                33.4996,
                126.5312,
                "KR",
                false,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 5)
        );
        setId(band, 10L);

        when(userRepository.findByIdAndIsDeletedFalse(2L)).thenReturn(Optional.of(otherUser));
        when(bandRepository.findById(10L)).thenReturn(Optional.of(band));

        ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> bandService.getOrRefreshInviteCode(2L, 10L)
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private void setId(Object target, Long id) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setInviteCodeExpiredAt(Band band, LocalDateTime value) {
        try {
            java.lang.reflect.Field field = Band.class.getDeclaredField("inviteCodeExpiredAt");
            field.setAccessible(true);
            field.set(band, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

