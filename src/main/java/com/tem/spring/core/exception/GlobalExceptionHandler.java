package com.tem.spring.core.exception;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 전역 보안 예외 처리기: 민감한 스택트레이스 및 API 키 노출 방어
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value
    @Builder
    public static class ErrorResponse {
        LocalDateTime timestamp;
        int status;
        String error;
        String message;
        Map<String, String> details;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );

        log.warn("[Security] Validation failed: {}", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error("Validation Error")
                        .message("요청 파라미터 유효성 검증에 실패했습니다.")
                        .details(errors)
                        .build()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("[Security] Invalid argument request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error("Bad Request")
                        .message(sanitizeMessage(ex.getMessage()))
                        .build()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("[Security] Unhandled internal exception occurred", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .error("Internal Server Error")
                        .message("서버 내부 처리 중 오류가 발생했습니다. (보안을 위해 상세 로그는 보호됩니다)")
                        .build()
        );
    }

    private String sanitizeMessage(String message) {
        if (message == null) return "Invalid input";
        // API 키 패턴 마스킹
        return message.replaceAll("(?i)(api[-_]?key|secret|token|password)[=:\\s]+([a-zA-Z0-9_-]{6,})", "=******");
    }
}
