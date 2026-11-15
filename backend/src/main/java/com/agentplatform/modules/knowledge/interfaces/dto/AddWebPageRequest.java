package com.agentplatform.modules.knowledge.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 添加网页到数据集的请求 DTO。
 *
 * @param url    网页地址，必填，仅支持 http/https
 * @param name   文档名称，可选；为空时使用网页标题
 * @param cookie 可选 Cookie 请求头，用于需要登录的内部 Wiki
 */
public record AddWebPageRequest(@NotBlank String url, String name, String cookie) {
}
