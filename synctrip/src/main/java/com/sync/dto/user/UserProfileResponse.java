package com.sync.dto.user;

import com.sync.domain.user.OauthProvider;

public record UserProfileResponse(
        Long id,
        String email,
        String name,
        String profileImageUrl,
        OauthProvider oauthProvider
) {
}
