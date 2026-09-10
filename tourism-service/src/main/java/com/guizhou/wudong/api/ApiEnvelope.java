package com.guizhou.wudong.api;

public record ApiEnvelope<T>(boolean success, T data, String message, String code, Object details) {
    public ApiEnvelope(boolean success, T data, String message) {
        this(success, data, message, null, null);
    }

    public static <T> ApiEnvelope<T> ok(T data) {
        return new ApiEnvelope<>(true, data, null, null, null);
    }

    public static <T> ApiEnvelope<T> fail(String message) {
        return fail(null, message);
    }

    public static <T> ApiEnvelope<T> fail(String code, String message) {
        return new ApiEnvelope<>(false, null, message, code, null);
    }

    public static <T> ApiEnvelope<T> fail(String code, String message, Object details) {
        return new ApiEnvelope<>(false, null, message, code, details);
    }
}
