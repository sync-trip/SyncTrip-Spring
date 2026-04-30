package com.sync.dto.auth;

public record LoginResponse(
        long userId,
        String email,
        String name,
        String profileImageUrl,
        boolean newUser,
        String accessToken,
        String refreshToken,
        long accessTokenExpiresIn,
        long refreshTokenExpiresIn
) {
}

