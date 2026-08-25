package com.zoomedu.platform.identity;

import java.time.LocalDateTime;

record UserRow(
        Long id,
        String username,
        String displayName,
        String email,
        String phone,
        String preferredLanguage,
        String timezone,
        UserStatus status,
        int failedLoginAttempts,
        LocalDateTime lockedUntil,
        LocalDateTime lastLoginAt,
        LocalDateTime passwordChangedAt,
        int version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
