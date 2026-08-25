package com.zoomedu.platform.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
class TokenService {

    private final AuthSessionStore authSessionStore;
    private final Clock clock;
    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;

    TokenService(
            AuthSessionStore authSessionStore,
            Clock clock,
            JwtEncoder jwtEncoder,
            SecurityProperties properties) {
        this.authSessionStore = authSessionStore;
        this.clock = clock;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    IssuedSession issueSession(AuthenticatedUser user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer(properties.issuer())
                .subject(user.id().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("username", user.username())
                .claim("displayName", user.displayName())
                .claim("preferredLanguage", user.preferredLanguage())
                .claim("timezone", user.timezone())
                .claim("dataScope", user.dataScope().name())
                .claim("roles", user.roles())
                .claim("permissions", user.permissions())
                .claim("campusIds", user.campusIds())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        String refreshToken = authSessionStore.createRefreshToken(user);
        AccessTokenResponse response = new AccessTokenResponse(
                "Bearer",
                accessToken,
                properties.accessTokenTtl().toSeconds(),
                user);
        return new IssuedSession(response, refreshToken);
    }
}
