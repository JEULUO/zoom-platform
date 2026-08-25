package com.zoomedu.platform.identity;

import java.time.LocalDateTime;
import java.util.List;

public record UserDetail(
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
        List<UserRoleAssignment> roles,
        List<UserCampusAssignment> campuses,
        int version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public UserDetail {
        roles = List.copyOf(roles);
        campuses = List.copyOf(campuses);
    }
}
