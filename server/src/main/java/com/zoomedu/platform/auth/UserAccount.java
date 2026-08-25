package com.zoomedu.platform.auth;

import java.time.LocalDateTime;

record UserAccount(
        Long id,
        String username,
        String passwordHash,
        String displayName,
        String preferredLanguage,
        String timezone,
        String status,
        int failedLoginAttempts,
        LocalDateTime lockedUntil) {
}
