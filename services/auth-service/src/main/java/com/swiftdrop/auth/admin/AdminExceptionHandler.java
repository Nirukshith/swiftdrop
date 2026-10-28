package com.swiftdrop.auth.admin;

import com.swiftdrop.common.CorrelationIdFilter;
import com.swiftdrop.common.ErrorResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice(basePackages = "com.swiftdrop.auth.admin")
public class AdminExceptionHandler {

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            new ErrorResponse(
                "EMAIL_ALREADY_EXISTS",
                ex.getMessage(),
                getCorrelationId(),
                Instant.now()
            )
        );
    }

    @ExceptionHandler(InvalidStaffRoleException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRole(InvalidStaffRoleException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            new ErrorResponse(
                "INVALID_ROLE",
                ex.getMessage(),
                getCorrelationId(),
                Instant.now()
            )
        );
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            new ErrorResponse(
                "USER_NOT_FOUND",
                ex.getMessage(),
                getCorrelationId(),
                Instant.now()
            )
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining("; "));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            new ErrorResponse(
                "VALIDATION_ERROR",
                message.isBlank() ? "Invalid request payload" : message,
                getCorrelationId(),
                Instant.now()
            )
        );
    }

    private String getCorrelationId() {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        return (correlationId != null && !correlationId.isBlank()) ? correlationId : UUID.randomUUID().toString();
    }
}
