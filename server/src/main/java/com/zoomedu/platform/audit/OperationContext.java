package com.zoomedu.platform.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;

public record OperationContext(
        Long userId,
        String username,
        String requestId,
        String httpMethod,
        String requestPath,
        String ipAddress) {

    public static OperationContext from(Jwt jwt, HttpServletRequest request) {
        return new OperationContext(
                Long.valueOf(jwt.getSubject()),
                truncate(jwt.getClaimAsString("username"), 64),
                truncate(request.getHeader("X-Request-Id"), 64),
                truncate(request.getMethod(), 12),
                truncate(request.getRequestURI(), 255),
                truncate(request.getRemoteAddr(), 45));
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
