package com.military.combat.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 错误响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiErrorResponse {
    private long timestamp;
    private int status;
    private String error;
    private String code;
    private String message;
    private String path;
}
