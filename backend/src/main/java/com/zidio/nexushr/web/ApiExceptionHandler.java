package com.zidio.nexushr.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * A rejected request stays a short JSON message. Validation errors keep
 * Spring's normal status so a form does not turn into a server failure.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException ex) {
        String message = ex.getReason();
        if (message == null || message.isBlank()) {
            message = "Request could not be completed.";
        }
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", message));
    }
}
