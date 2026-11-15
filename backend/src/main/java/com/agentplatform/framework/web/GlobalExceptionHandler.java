package com.agentplatform.framework.web;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 *
 * <p>位于技术框架的 Web 层，把各类异常统一映射为 {@link ApiResponse} 与对应 HTTP 状态码，
 * 保证前端始终拿到一致结构。业务异常保留其 {@link ErrorCode}；
 * 未预期异常只记录日志并返回通用提示，避免泄露堆栈或内部信息。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常。
     *
     * @param ex 业务异常
     * @return 状态码取自错误码的失败响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.errorCode();
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResponse.error(code.name(), ex.getMessage()));
    }

    /**
     * 处理方法级授权拒绝（{@code @PreAuthorize} 不通过）。
     *
     * @param ex 访问拒绝异常
     * @return 403 失败响应
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(403).body(ApiResponse.error(ErrorCode.FORBIDDEN.name(), "没有权限执行该操作"));
    }

    /**
     * 处理认证失败（Token 缺失、过期、被注销等）。
     *
     * @param ex 认证异常
     * @return 401 失败响应
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(401).body(ApiResponse.error(ErrorCode.UNAUTHENTICATED.name(), "未登录或登录已过期"));
    }

    /**
     * 处理 {@code @Valid} 参数校验失败。
     *
     * <p>只返回第一个字段错误，格式为「字段名: 提示」，避免一次性暴露过多内部字段细节。</p>
     *
     * @param ex 参数校验异常
     * @return 400 失败响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        FieldError error = ex.getBindingResult().getFieldError();
        String message = error == null ? "参数校验失败" : error.getField() + ": " + error.getDefaultMessage();
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.VALIDATION_ERROR.name(), message));
    }

    /**
     * 兜底处理未预期异常。
     *
     * <p>记录完整堆栈到服务端日志，但对外只返回通用文案，防止敏感信息外泄。</p>
     *
     * @param ex 任意未处理异常
     * @return 500 失败响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("unexpected error", ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.name(), "服务内部错误"));
    }
}
