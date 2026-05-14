package com.sync.dto.band;

import java.time.LocalDateTime;

/**
 * 밴드 초대하기 버튼을 눌렀을 때 프론트로 내려주는 응답
 * - inviteCode: 실제 가입에 사용하는 초대코드
 * - inviteCodeExpiredAt: 초대코드 만료 시각
 * - inviteShareLink: 카카오톡/문자 공유용 웹 링크
 * - inviteDeepLink: 앱이 설치된 경우 바로 열기 위한 딥링크
 */
public record BandInviteCodeResponse(
    Long bandId,
    String inviteCode,
    LocalDateTime inviteCodeExpiredAt,
    String inviteShareLink,
    String inviteDeepLink
) {
}


