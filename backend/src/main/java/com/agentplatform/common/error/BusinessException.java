package com.agentplatform.common.error;

/**
 * 业务异常。
 *
 * <p>位于共享内核（common）层，应用/接口层抛出该异常表达可预期的业务失败，
 * 由 {@link com.agentplatform.framework.web.GlobalExceptionHandler} 统一映射为 HTTP 状态码与 {@code ApiResponse}。
 * 非业务性异常不应使用该类型，以免泄露内部细节。</p>
 */
public class BusinessException extends RuntimeException {
    /** 关联的错误码，决定 HTTP 状态码与默认提示 */
    private final ErrorCode errorCode;

    /**
     * 使用错误码的默认提示构造异常。
     *
     * @param errorCode 错误码
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    /**
     * 使用自定义提示构造异常。
     *
     * @param errorCode 错误码
     * @param message   面向用户的提示信息，应避免包含敏感数据
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 获取错误码。
     *
     * @return 关联的错误码
     */
    public ErrorCode errorCode() { return errorCode; }
}
