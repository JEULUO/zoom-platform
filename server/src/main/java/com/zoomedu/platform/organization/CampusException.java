package com.zoomedu.platform.organization;

import org.springframework.http.HttpStatus;

class CampusException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    CampusException(String code, String message, HttpStatus status) {
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
