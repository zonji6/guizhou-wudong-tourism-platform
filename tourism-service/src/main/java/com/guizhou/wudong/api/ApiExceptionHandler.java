package com.guizhou.wudong.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiRequestException.class)
    ResponseEntity<ApiEnvelope<Void>> apiRequest(ApiRequestException exception) {
        return ResponseEntity.status(exception.status())
                .body(ApiEnvelope.fail(exception.code(), exception.getMessage(), exception.details()));
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiEnvelope<Void> notFound(NotFoundException exception) {
        return ApiEnvelope.fail("RESOURCE_NOT_FOUND", "未找到或无权访问该资源");
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiEnvelope<Void> badRequest(Exception exception) {
        return ApiEnvelope.fail("VALIDATION_FAILED", "请求字段不符合要求");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    ApiEnvelope<Void> internalError(Exception exception) {
        return ApiEnvelope.fail("INTERNAL_ERROR", "服务暂时无法处理请求");
    }
}
