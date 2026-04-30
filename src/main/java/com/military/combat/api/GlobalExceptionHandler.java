package com.military.combat.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：把服务层参数校验异常统一转换为结构化 JSON。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.BAD_REQUEST,
                "BAD_REQUEST",
                "INVALID_ARGUMENT",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(
            IllegalStateException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.CONFLICT,
                "CONFLICT",
                "INVALID_STATE",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntime(
            RuntimeException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "RUNTIME_ERROR",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String error,
            String code,
            String message,
            String path
    ) {
        ApiErrorResponse body = new ApiErrorResponse(
                System.currentTimeMillis(),
                status.value(),
                error,
                code,
                message,
                path
        );
        return ResponseEntity.status(status).body(body);
    }
}
