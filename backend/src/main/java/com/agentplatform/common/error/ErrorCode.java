package com.agentplatform.common.error;

public enum ErrorCode {
    VALIDATION_ERROR(400, "参数校验失败"),
    UNAUTHENTICATED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有权限执行该操作"),
    NOT_FOUND(404, "资源不存在"),
    NAME_CONFLICT(409, "名称已存在"),
    STATE_CONFLICT(409, "当前状态不允许该操作"),
    INTERNAL_ERROR(500, "服务内部错误");

    private final int httpStatus;
    private final String message;

    ErrorCode(int httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public int httpStatus() { return httpStatus; }
    public String message() { return message; }
}
