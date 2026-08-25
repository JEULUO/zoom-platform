package com.zoomedu.platform.auth;

import org.springframework.http.HttpStatus;

class AuthFailureException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    AuthFailureException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    String code() {
        return code;
    }

    HttpStatus status() {
        return status;
    }
}
