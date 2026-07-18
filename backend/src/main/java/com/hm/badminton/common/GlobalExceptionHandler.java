package com.hm.badminton.common;

import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常翻译器。
 *
 * <p>Controller 和 Service 只需抛异常，这里统一决定 HTTP 状态和 {@link ApiResponse} 内容。
 * 业务异常给用户可理解的提示，未知异常隐藏内部堆栈，详细原因只应写入服务端日志。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(BusinessException e, HttpServletRequest request) {
        // 业务 4xx 也记录请求路径，线上遇到 AI/登录等偶发拒绝时可直接从 journal 定位原因。
        log.warn("Business request rejected: method={}, uri={}, code={}, message={}",
                request.getMethod(), request.getRequestURI(), e.code(), e.getMessage());
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
