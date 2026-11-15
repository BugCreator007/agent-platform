package com.agentplatform.common.api;

import java.time.Instant;

/**
 * 统一 API 响应包装。
 *
 * <p>位于共享内核（common）层，所有 REST 接口均返回该结构，
 * 前端通过 {@code success}/{@code code} 判断结果，{@code data} 承载业务数据。</p>
 *
 * @param <T>       业务数据类型
 * @param success   是否成功
 * @param code      业务码：成功固定为 {@code OK}，失败为 {@link com.agentplatform.common.error.ErrorCode} 的枚举名
 * @param message   提示信息
 * @param data      业务数据，失败时为 {@code null}
 * @param timestamp 响应生成时间
 */
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        Instant timestamp
) {
    /**
     * 构造成功响应。
     *
     * @param data 业务数据，允许为 {@code null}
     * @param <T>  业务数据类型
     * @return 成功包装的响应体
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", "success", data, Instant.now());
    }

    /**
     * 构造失败响应。
     *
     * @param code    业务错误码
     * @param message 提示信息
     * @param <T>     业务数据类型
     * @return 失败包装的响应体，{@code data} 为 {@code null}
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, code, message, null, Instant.now());
    }
}
