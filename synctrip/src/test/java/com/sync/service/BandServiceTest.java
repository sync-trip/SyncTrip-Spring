package com.sync.service;

import com.sync.domain.band.Band;
import com.sync.domain.band.BandMember;
import com.sync.domain.band.BandRole;
import com.sync.domain.band.BandStatus;
import com.sync.domain.user.User;
import com.sync.config.BandInviteProperties;
import com.sync.dto.band.BandInviteCodeResponse;
import com.sync.dto.band.BandReadyResponse;
import com.sync.dto.band.BandResponse;
import com.sync.dto.band.BandStatusTransitionResponse;
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

    @Test
    void markReady_advancesBandToVotingWhenAllMembersAreReady() {
        User owner = User.kakaoUser("owner@example.com", "owner", null, "100");
        setId(owner, 1L);

        User other = User.kakaoUser("other@example.com", "other", null, "200");
        setId(other, 2L);

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

        BandMember ownerMember = BandMember.create(owner, band, BandRole.OWNER);
        setId(ownerMember, 101L);
        BandMember otherMember = BandMember.create(other, band, BandRole.MEMBER);
        setId(otherMember, 102L);

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(owner));
        when(userRepository.findByIdAndIsDeletedFalse(2L)).thenReturn(Optional.of(other));
        when(bandRepository.findById(10L)).thenReturn(Optional.of(band));
        when(bandMemberRepository.findByBandIdAndUserId(10L, 1L)).thenReturn(Optional.of(ownerMember));
        when(bandMemberRepository.findByBandIdAndUserId(10L, 2L)).thenReturn(Optional.of(otherMember));
        when(bandMemberRepository.countByBandId(10L)).thenReturn(2L);
        when(bandMemberRepository.countByBandIdAndIsReadyTrue(10L)).thenAnswer(invocation -> {
            long count = 0L;
            if (ownerMember.isReady()) {
                count++;
            }
            if (otherMember.isReady()) {
                count++;
            }
            return count;
        });
        when(bandMemberRepository.save(any(BandMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bandRepository.save(any(Band.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BandReadyResponse firstResponse = bandService.markReady(1L, 10L);
        BandReadyResponse secondResponse = bandService.markReady(2L, 10L);

        assertThat(firstResponse.isReady()).isTrue();
        assertThat(firstResponse.readyCount()).isEqualTo(1L);
        assertThat(firstResponse.allReady()).isFalse();
        assertThat(firstResponse.bandStatus()).isEqualTo(BandStatus.PLANNING);

        assertThat(secondResponse.isReady()).isTrue();
        assertThat(secondResponse.readyCount()).isEqualTo(2L);
        assertThat(secondResponse.allReady()).isTrue();
        assertThat(secondResponse.bandStatus()).isEqualTo(BandStatus.VOTING);
        assertThat(band.getStatus()).isEqualTo(BandStatus.VOTING);
    }

    @Test
    void markNotReady_clearsReadyStateWithoutAdvancingBand() {
        User user = User.kakaoUser("user@example.com", "user", null, "100");
        setId(user, 1L);

        Band band = Band.create(
                user,
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

        BandMember member = BandMember.create(user, band, BandRole.OWNER);
        setId(member, 101L);
        member.updateReady(true);

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandRepository.findById(10L)).thenReturn(Optional.of(band));
        when(bandMemberRepository.findByBandIdAndUserId(10L, 1L)).thenReturn(Optional.of(member));
        when(bandMemberRepository.countByBandId(10L)).thenReturn(1L);
        when(bandMemberRepository.countByBandIdAndIsReadyTrue(10L)).thenAnswer(invocation -> member.isReady() ? 1L : 0L);
        when(bandMemberRepository.save(any(BandMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BandReadyResponse response = bandService.markNotReady(1L, 10L);

        assertThat(response.isReady()).isFalse();
        assertThat(response.readyCount()).isEqualTo(0L);
        assertThat(response.allReady()).isFalse();
        assertThat(response.bandStatus()).isEqualTo(BandStatus.PLANNING);
        assertThat(member.isReady()).isFalse();
    }

    @Test
    void advanceBandStatus_advancesOnlyOwnerBand() {
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

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(owner));
        when(bandRepository.findById(10L)).thenReturn(Optional.of(band));
        when(bandRepository.save(any(Band.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BandStatusTransitionResponse response = bandService.advanceBandStatus(1L, 10L);

        assertThat(response.bandId()).isEqualTo(10L);
        assertThat(response.previousStatus()).isEqualTo(BandStatus.PLANNING);
        assertThat(response.currentStatus()).isEqualTo(BandStatus.VOTING);
        assertThat(band.getStatus()).isEqualTo(BandStatus.VOTING);
    }

    @Test
    void getMyBands_returnsAllBandsUserIsPartOf() {
        User user = User.kakaoUser("user@example.com", "사용자", null, "100");
        setId(user, 1L);

        Band band1 = Band.create(
                user,
                "봄여행",
                "제주도",
                33.4996,
                126.5312,
                "KR",
                false,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 5)
        );
        setId(band1, 10L);

        Band band2 = Band.create(
                user,
                "여름여행",
                "강릉",
                37.7510,
                128.8889,
                "KR",
                false,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 7)
        );
        setId(band2, 11L);

        BandMember member1 = BandMember.create(user, band1, BandRole.OWNER);
        setId(member1, 101L);

        BandMember member2 = BandMember.create(user, band2, BandRole.MEMBER);
        setId(member2, 102L);

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandMemberRepository.findByUserId(1L)).thenReturn(java.util.List.of(member1, member2));

        java.util.List<BandResponse> responses = bandService.getMyBands(1L);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).id()).isEqualTo(10L);
        assertThat(responses.get(0).name()).isEqualTo("봄여행");
        assertThat(responses.get(0).destination()).isEqualTo("제주도");
        assertThat(responses.get(1).id()).isEqualTo(11L);
        assertThat(responses.get(1).name()).isEqualTo("여름여행");
    }

    @Test
    void getMyBands_returnsEmptyListWhenUserHasNoBands() {
        User user = User.kakaoUser("user@example.com", "사용자", null, "100");
        setId(user, 1L);

        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(bandMemberRepository.findByUserId(1L)).thenReturn(java.util.List.of());

        java.util.List<BandResponse> responses = bandService.getMyBands(1L);

        assertThat(responses).isEmpty();
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

