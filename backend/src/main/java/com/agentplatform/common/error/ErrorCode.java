package com.agentplatform.common.error;

/**
 * 业务错误码枚举。
 *
 * <p>位于共享内核（common）层，集中定义错误码、对应的 HTTP 状态码与默认提示，
 * 由全局异常处理器统一映射，避免各接口散落状态码字符串。</p>
 */
public enum ErrorCode {
    /** 参数校验失败（400） */
    VALIDATION_ERROR(400, "参数校验失败"),
    /** 未登录或 Token 失效/已注销（401） */
    UNAUTHENTICATED(401, "未登录或登录已过期"),
    /** 已认证但无操作权限（403） */
    FORBIDDEN(403, "没有权限执行该操作"),
    /** 资源不存在（404） */
    NOT_FOUND(404, "资源不存在"),
    /** 名称等业务唯一键冲突（409） */
    NAME_CONFLICT(409, "名称已存在"),
    /** 当前状态不允许该操作（409） */
    STATE_CONFLICT(409, "当前状态不允许该操作"),
    /** 请求过于频繁（429） */
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),
    /** 未预期的内部错误（500） */
    INTERNAL_ERROR(500, "服务内部错误");

    /** 映射的 HTTP 状态码 */
    private final int httpStatus;
    /** 面向用户的默认提示 */
    private final String message;

    /**
     * @param httpStatus HTTP 状态码
     * @param message    默认提示
     */
    ErrorCode(int httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    /**
     * HTTP 状态码。
     *
     * @return 状态码数值
     */
    public int httpStatus() { return httpStatus; }

    /**
     * 默认提示信息。
     *
     * @return 提示文本
     */
    public String message() { return message; }
}
