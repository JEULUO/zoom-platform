package com.zoomedu.platform.identity;

import com.zoomedu.platform.auth.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = UserDirectoryController.class)
class UserDirectoryExceptionHandler {

    private final Clock clock;

    UserDirectoryExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(UserDirectoryException.class)
    ResponseEntity<ApiErrorResponse> handleDirectoryFailure(
            UserDirectoryException exception,
            HttpServletRequest request) {
        return response(exception.status(), exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiErrorResponse> handleInvalidRequest(
            Exception exception,
            HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "User directory request contains invalid values",
                request);
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                clock.instant(), status.value(), code, message, request.getRequestURI()));
    }
}
