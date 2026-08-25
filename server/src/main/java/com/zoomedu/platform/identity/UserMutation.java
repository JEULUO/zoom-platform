package com.zoomedu.platform.identity;

record UserMutation(
        String username,
        String passwordHash,
        String displayName,
        String email,
        String phone,
        String preferredLanguage,
        String timezone,
        UserStatus status) {
}
