package com.sync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 밴드 초대 링크 생성용 설정값을 담는 프로퍼티
 * - shareBaseUrl: 사용자가 카카오톡/문자로 공유할 HTTPS 링크
 * - deepLinkBaseUrl: 안드로이드 앱이 바로 열도록 쓰는 커스텀 딥링크
 * - 국내/해외 지도 API와는 별개로, 초대 링크는 앱 진입점만 담당한다.
 * - 실제 도메인 값은 application-*.yml 또는 환경변수(GitHub Actions/Secrets)에서 주입한다.
 */
@ConfigurationProperties(prefix = "app.invite")
public record BandInviteProperties(
    String shareBaseUrl,
    String deepLinkBaseUrl
) {
    public BandInviteProperties {
        // 설정값이 비어 있으면 런타임에서 바로 알 수 있도록 명확한 예외를 던진다.
        if (!StringUtils.hasText(shareBaseUrl)) {
            throw new IllegalArgumentException("app.invite.share-base-url 설정이 필요합니다.");
        }
        if (!StringUtils.hasText(deepLinkBaseUrl)) {
            throw new IllegalArgumentException("app.invite.deep-link-base-url 설정이 필요합니다.");
        }
    }
}

