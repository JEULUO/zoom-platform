package com.zoomedu.platform.auth;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginAuditService {

    private final AuthMapper authMapper;
    private final Clock clock;
    private final SecurityProperties properties;

    LoginAuditService(AuthMapper authMapper, Clock clock, SecurityProperties properties) {
        this.authMapper = authMapper;
        this.clock = clock;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUnknownUser(String username, ClientRequestContext context) {
        insertAudit(null, username, "LOGIN", false, "INVALID_CREDENTIALS", context, now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPasswordFailure(UserAccount account, ClientRequestContext context) {
        LocalDateTime now = now();
        authMapper.recordFailedPassword(
                account.id(),
                properties.maxLoginAttempts(),
                now.plus(properties.lockDuration()),
                now);
        insertAudit(account.id(), account.username(), "LOGIN", false, "INVALID_CREDENTIALS", context, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUnavailableAccount(UserAccount account, ClientRequestContext context) {
        insertAudit(account.id(), account.username(), "LOGIN", false, "ACCOUNT_" + account.status(), context, now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccessfulLogin(UserAccount account, ClientRequestContext context) {
        LocalDateTime now = now();
        authMapper.recordSuccessfulLogin(account.id(), now);
        insertAudit(account.id(), account.username(), "LOGIN", true, null, context, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSessionEvent(
            Long userId,
            String username,
            String eventType,
            boolean success,
            String failureReason,
            ClientRequestContext context) {
        insertAudit(userId, username, eventType, success, failureReason, context, now());
    }

    private void insertAudit(
            Long userId,
            String username,
            String eventType,
            boolean success,
            String failureReason,
            ClientRequestContext context,
            LocalDateTime occurredAt) {
        authMapper.insertLoginAudit(
                userId,
                username,
                eventType,
                success,
                failureReason,
                context.ipAddress(),
                context.userAgent(),
                context.requestId(),
                occurredAt);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
