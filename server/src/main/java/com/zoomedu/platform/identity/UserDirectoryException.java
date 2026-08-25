package com.zoomedu.platform.identity;

import org.springframework.http.HttpStatus;

class UserDirectoryException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    UserDirectoryException(String code, String message, HttpStatus status) {
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
