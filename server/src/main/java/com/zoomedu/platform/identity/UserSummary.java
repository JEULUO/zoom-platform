package com.zoomedu.platform.identity;

import java.time.LocalDateTime;
import java.util.List;

public record UserSummary(
        Long id,
        String username,
        String displayName,
        String email,
        String phone,
        UserStatus status,
        List<UserRoleAssignment> roles,
        List<UserCampusAssignment> campuses,
        LocalDateTime lastLoginAt,
        int version,
        LocalDateTime updatedAt) {

    public UserSummary {
        roles = List.copyOf(roles);
        campuses = List.copyOf(campuses);
    }
}
