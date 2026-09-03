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
        String code;
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
                        .code("INVALID_ARGUMENT")
                        .error("Validation Error")
                        .message("요청 파라미터 유효성 검증에 실패했습니다.")
                        .details(errors)
                        .build()
        );
    }

    @ExceptionHandler(com.tem.spring.core.ratelimit.RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitException(com.tem.spring.core.ratelimit.RateLimitExceededException ex) {
        log.warn("[RateLimit] Rate limit exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.TOO_MANY_REQUESTS.value())
                        .code("RATE_LIMIT_EXCEEDED")
                        .error("Too Many Requests (Rate Limit Exceeded)")
                        .message(ex.getMessage())
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
                        .code("BAD_REQUEST")
                        .error("Bad Request")
                        .message(sanitizeMessage(ex.getMessage()))
                        .build()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("[Security] Unhandled internal exception occurred (obfuscated for client): {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .code("ENGINE_TEMPORARY_BUSY")
                        .error("Institutional Engine Service Unavailable")
                        .message("AETHER 기관급 퀀트 엔진 연산 세션이 일시적으로 집중되었습니다. 잠시 후 다시 시도해 주세요.")
                        .build()
        );
    }

    private String sanitizeMessage(String message) {
        if (message == null) return "Invalid input";
        // API 키 패턴 마스킹
        return message.replaceAll("(?i)(api[-_]?key|secret|token|password)[=:\\s]+([a-zA-Z0-9_-]{6,})", "=******");
    }
}
