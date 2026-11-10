package com.example.pagepermission.web;

import com.example.pagepermission.core.PagePermissionException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(PagePermissionException.class)
    ResponseEntity<Map<String, String>> handle(PagePermissionException ex) {
        return ResponseEntity.status(403).body(Map.of(
                "error", ex.error().name(),
                "message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> handleControllerFailure(IllegalStateException ex) {
        return ResponseEntity.internalServerError().body(Map.of(
                "error", "CONTROLLER_FAILED",
                "message", ex.getMessage()));
    }
}
