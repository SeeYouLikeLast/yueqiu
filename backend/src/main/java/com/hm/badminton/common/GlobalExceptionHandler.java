package com.hm.badminton.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> business(BusinessException e) {
        return ApiResponse.fail(e.code(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ApiResponse<Void> validation(Exception e) {
        return ApiResponse.fail(422, "参数校验失败：" + e.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ApiResponse<Void> duplicate(DuplicateKeyException e) {
        return ApiResponse.fail(409, "数据已存在，请勿重复提交");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> unreadable(HttpMessageNotReadableException e) {
        return ApiResponse.fail(400, "请求体格式不正确");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> other(Exception e) {
        return ApiResponse.fail(500, "系统繁忙，请稍后再试");
    }
}

