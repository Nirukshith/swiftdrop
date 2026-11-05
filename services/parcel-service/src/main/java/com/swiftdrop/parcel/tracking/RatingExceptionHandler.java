package com.swiftdrop.parcel.tracking;

import com.swiftdrop.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

/**
 * Error bodies for the rating endpoints with the codes in parcel-read.yaml. Validation errors use
 * the contract's VALIDATION_FAILED, and a malformed body is a 400 instead of a 500.
 */
@RestControllerAdvice(assignableTypes = RatingController.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class RatingExceptionHandler {

    static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    @ExceptionHandler(RatingException.class)
    ResponseEntity<ErrorResponse> rating(RatingException ex, HttpServletRequest request) {
        return json(ex.status(), ex.code(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return json(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, message.isEmpty() ? "Invalid rating" : message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return json(HttpStatus.BAD_REQUEST, VALIDATION_FAILED,
                "Request body must be JSON with an integer score from 1 to 5 and an optional comment", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> unsupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return json(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Send the rating as application/json", request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> responseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == HttpStatus.NOT_FOUND) {
            return json(ex.getStatusCode(), RatingException.NOT_FOUND, "Parcel not found", request);
        }
        String code = status != null ? status.name() : String.valueOf(ex.getStatusCode().value());
        return json(ex.getStatusCode(), code, ex.getReason() != null ? ex.getReason() : "Request failed", request);
    }

    private static ResponseEntity<ErrorResponse> json(HttpStatusCode status, String code, String message,
                                                      HttpServletRequest request) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(TrackingErrors.body(code, message, request));
    }
}
