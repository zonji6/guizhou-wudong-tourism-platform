package com.guizhou.wudong.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public final class VisitorIdentity {
    private VisitorIdentity() {
    }

    public static String require(HttpServletRequest request) {
        String value = request.getHeader("X-Visitor-Id");
        if (value == null) {
            throw new ApiRequestException(HttpStatus.BAD_REQUEST, "VISITOR_ID_REQUIRED", "请先建立本机游客身份");
        }
        if (value.isBlank()) {
            throw invalid();
        }
        try {
            String normalized = UUID.fromString(value).toString();
            if (!normalized.equals(value)) {
                throw invalid();
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static ApiRequestException invalid() {
        return new ApiRequestException(HttpStatus.BAD_REQUEST, "VISITOR_ID_INVALID", "本机游客身份格式无效");
    }
}
