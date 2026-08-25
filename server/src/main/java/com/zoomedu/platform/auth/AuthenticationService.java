package com.zoomedu.platform.auth;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
class AuthenticationService {

    private final AuthMapper authMapper;
    private final AuthSessionStore authSessionStore;
    private final Clock clock;
    private final LoginAuditService loginAuditService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    AuthenticationService(
            AuthMapper authMapper,
            AuthSessionStore authSessionStore,
            Clock clock,
            LoginAuditService loginAuditService,
            PasswordEncoder passwordEncoder,
            TokenService tokenService) {
        this.authMapper = authMapper;
        this.authSessionStore = authSessionStore;
        this.clock = clock;
        this.loginAuditService = loginAuditService;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    IssuedSession login(LoginRequest request, ClientRequestContext context) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        UserAccount account = authMapper.findByUsername(username);
        if (account == null) {
            loginAuditService.recordUnknownUser(username, context);
            throw invalidCredentials();
        }

        account = unlockExpiredAccount(account);
        if ("LOCKED".equals(account.status())) {
            loginAuditService.recordUnavailableAccount(account, context);
            throw new AuthFailureException(
                    "ACCOUNT_LOCKED",
                    "Account is temporarily locked",
                    HttpStatus.LOCKED);
        }
        if (!"ACTIVE".equals(account.status())) {
            loginAuditService.recordUnavailableAccount(account, context);
            throw new AuthFailureException(
                    "ACCOUNT_UNAVAILABLE",
                    "Account is not available",
                    HttpStatus.FORBIDDEN);
        }
        if (!passwordEncoder.matches(request.password(), account.passwordHash())) {
            loginAuditService.recordPasswordFailure(account, context);
            throw invalidCredentials();
        }

        AuthenticatedUser user = loadUser(account);
        IssuedSession issuedSession = tokenService.issueSession(user);
        loginAuditService.recordSuccessfulLogin(account, context);
        return issuedSession;
    }

    IssuedSession refresh(String refreshToken, ClientRequestContext context) {
        RefreshSession refreshSession = authSessionStore.consumeRefreshToken(refreshToken)
                .orElseThrow(() -> {
                    loginAuditService.recordSessionEvent(
                            null, "<refresh>", "TOKEN_REFRESH", false, "INVALID_REFRESH_TOKEN", context);
                    return new AuthFailureException(
                            "INVALID_REFRESH_TOKEN",
                            "Refresh session is invalid or expired",
                            HttpStatus.UNAUTHORIZED);
                });

        UserAccount account = authMapper.findById(refreshSession.userId());
        if (account == null || !"ACTIVE".equals(account.status())) {
            loginAuditService.recordSessionEvent(
                    refreshSession.userId(),
                    refreshSession.username(),
                    "TOKEN_REFRESH",
                    false,
                    "ACCOUNT_UNAVAILABLE",
                    context);
            throw new AuthFailureException(
                    "ACCOUNT_UNAVAILABLE",
                    "Account is not available",
                    HttpStatus.UNAUTHORIZED);
        }

        IssuedSession issuedSession = tokenService.issueSession(loadUser(account));
        loginAuditService.recordSessionEvent(
                account.id(), account.username(), "TOKEN_REFRESH", true, null, context);
        return issuedSession;
    }

    void logout(Jwt jwt, String refreshToken, ClientRequestContext context) {
        authSessionStore.revokeRefreshToken(refreshToken);
        authSessionStore.revokeAccessToken(jwt.getId(), jwt.getExpiresAt(), clock.instant());
        loginAuditService.recordSessionEvent(
                parseUserId(jwt),
                jwt.getClaimAsString("username"),
                "LOGOUT",
                true,
                null,
                context);
    }

    AuthenticatedUser currentUser(Jwt jwt) {
        UserAccount account = authMapper.findById(parseUserId(jwt));
        if (account == null || !"ACTIVE".equals(account.status())) {
            throw new AuthFailureException(
                    "ACCOUNT_UNAVAILABLE",
                    "Account is not available",
                    HttpStatus.UNAUTHORIZED);
        }
        return loadUser(account);
    }

    private UserAccount unlockExpiredAccount(UserAccount account) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if ("LOCKED".equals(account.status())
                && account.lockedUntil() != null
                && !account.lockedUntil().isAfter(now)) {
            authMapper.unlockExpiredAccount(account.id(), now);
            return authMapper.findById(account.id());
        }
        return account;
    }

    private AuthenticatedUser loadUser(UserAccount account) {
        List<RoleGrant> roleGrants = authMapper.findRoleGrants(account.id());
        List<String> roles = roleGrants.stream().map(RoleGrant::code).toList();
        DataScope dataScope = DataScope.broadest(
                roleGrants.stream().map(RoleGrant::dataScope).toList());
        return new AuthenticatedUser(
                account.id(),
                account.username(),
                account.displayName(),
                account.preferredLanguage(),
                account.timezone(),
                dataScope,
                roles,
                authMapper.findPermissionCodes(account.id()),
                authMapper.findCampusIds(account.id()));
    }

    private Long parseUserId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new AuthFailureException(
                    "INVALID_ACCESS_TOKEN",
                    "Access token subject is invalid",
                    HttpStatus.UNAUTHORIZED);
        }
    }

    private AuthFailureException invalidCredentials() {
        return new AuthFailureException(
                "INVALID_CREDENTIALS",
                "Username or password is incorrect",
                HttpStatus.UNAUTHORIZED);
    }
}
