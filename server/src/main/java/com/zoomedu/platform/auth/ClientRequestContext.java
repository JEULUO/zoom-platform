package com.zoomedu.platform.auth;

import jakarta.servlet.http.HttpServletRequest;

record ClientRequestContext(
        String ipAddress,
        String userAgent,
        String requestId) {

    static ClientRequestContext from(HttpServletRequest request) {
        return new ClientRequestContext(
                truncate(request.getRemoteAddr(), 45),
                truncate(request.getHeader("User-Agent"), 512),
                truncate(request.getHeader("X-Request-Id"), 64));
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
