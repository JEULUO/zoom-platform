package com.zoomedu.platform.auth;

import java.util.List;

public record AuthenticatedUser(
        Long id,
        String username,
        String displayName,
        String preferredLanguage,
        String timezone,
        DataScope dataScope,
        List<String> roles,
        List<String> permissions,
        List<Long> campusIds) {

    public AuthenticatedUser {
        roles = List.copyOf(roles);
        permissions = List.copyOf(permissions);
        campusIds = List.copyOf(campusIds);
    }
}
