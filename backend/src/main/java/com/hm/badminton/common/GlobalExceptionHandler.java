package com.hm.badminton.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(BusinessException e) {
        return fail(e.code(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiResponse<Void>> validation(Exception e) {
        return fail(422, "参数校验失败: " + e.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> duplicate(DuplicateKeyException e) {
        return fail(409, "数据已存在，请勿重复提交");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> unreadable(HttpMessageNotReadableException e) {
        return fail(400, "请求体格式不正确");
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> notFound(Exception e) {
        return fail(404, "资源不存在");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> other(Exception e) {
        return fail(500, "系统异常，请稍后再试");
    }

    private ResponseEntity<ApiResponse<Void>> fail(int code, String message) {
        return ResponseEntity.status(statusOf(code)).body(ApiResponse.fail(code, message));
    }

    private HttpStatus statusOf(int code) {
        if (code >= 400 && code < 600) {
            return HttpStatus.valueOf(code);
        }
        return HttpStatus.BAD_REQUEST;
    }
}
