package com.zoomedu.platform.auth;

import java.time.Instant;

record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path) {
}
