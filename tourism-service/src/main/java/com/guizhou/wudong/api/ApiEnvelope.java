package com.guizhou.wudong.api;

public record ApiEnvelope<T>(boolean success, T data, String message) {
    public static <T> ApiEnvelope<T> ok(T data) {
        return new ApiEnvelope<>(true, data, null);
    }

    public static <T> ApiEnvelope<T> fail(String message) {
        return new ApiEnvelope<>(false, null, message);
    }
}
