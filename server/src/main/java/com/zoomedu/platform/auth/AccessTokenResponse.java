package com.zoomedu.platform.auth;

public record AccessTokenResponse(
        String tokenType,
        String accessToken,
        long expiresIn,
        AuthenticatedUser user) {
}
