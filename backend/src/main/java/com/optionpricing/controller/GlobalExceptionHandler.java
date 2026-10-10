package com.optionpricing.controller;

import com.optionpricing.dto.ApiErrorResponse;
import com.optionpricing.service.MarketDataUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Turns every failure into the same {message, timestamp} JSON shape. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiErrorResponse> illegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Invalid request.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiErrorResponse.of(message));
    }

    // Malformed JSON, or a value that does not fit its field (for example an unknown option type or a bad date).
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> unreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                "Request body is not valid JSON or contains a value of the wrong type."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> wrongParameterType(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(ex.getName() + " has an invalid value."));
    }

    @ExceptionHandler(MarketDataUnavailableException.class)
    ResponseEntity<ApiErrorResponse> marketDataUnavailable(MarketDataUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception ex) {
        // Spring's own request errors (unknown path, wrong method, missing parameter) carry their status.
        if (ex instanceof ErrorResponse springError) {
            HttpStatusCode status = springError.getStatusCode();
            String detail = springError.getBody().getDetail();
            return ResponseEntity.status(status).body(ApiErrorResponse.of(detail != null ? detail : "Request could not be processed."));
        }
        logger.error("Unhandled error", ex);
        return ResponseEntity.internalServerError().body(ApiErrorResponse.of("Something went wrong on our side."));
    }
}
