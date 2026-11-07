package com.swiftdrop.auth.reset;

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

@RestControllerAdvice(basePackages = "com.swiftdrop.auth.reset")
public class PasswordResetExceptionHandler {

    @ExceptionHandler(InvalidResetTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidResetToken(InvalidResetTokenException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            new ErrorResponse(
                "INVALID_RESET_TOKEN",
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

