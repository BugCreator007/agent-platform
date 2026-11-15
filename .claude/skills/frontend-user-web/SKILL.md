---
name: frontend-user-web
description: 修改用户端 user-web（React 19 + Vite，智能体目录与 SSE 流式聊天门户）时使用；覆盖用户门户 API、SSE 解析、会话持久化、停止生成和移动端体验。
---

# 用户端前端开发

## 技术栈

- React 19 + TypeScript + Vite。
- 端口 5174；开发代理 `/api` → 8081（可用 `VITE_PROXY_TARGET` 覆盖）。
- 没有 Ant Design/TanStack Query；轻量自定义 UI。
- 文件：`src/App.tsx`、`src/api.ts`、`src/styles.css`。

## 后端契约

用户端只走 `/api/v1/user/*`：

```text
GET  /api/v1/user/agents
POST /api/v1/user/chat:stream
GET  /api/v1/sessions/{id}/messages
POST /api/v1/auth/login
```

- 只展示 `running/grayscale` 智能体。
- 用户端视图不包含管理指标、提示词全文、知识库编码。
- 登录 token 存 `agent_user_token`，用户资料存 `agent_user_profile`。
- 401 清除本地登录态，回到登录页。

## SSE 客户端

`src/api.ts` 的 `streamChat`：

- `fetch` POST `/user/chat:stream`，携带 Bearer，`Accept: text/event-stream`。
- 不能用原生 EventSource（不能带 Authorization）。
- `ReadableStream` + `TextDecoder`，按 `\n\n` 切事件块，处理跨 chunk。
- 解析 `event:` / 多行 `data:`。
- 事件：
  - `meta`：sessionId、model、route、mock 等
  - `delta`：纯文本增量
  - `tool`：工具调用开始
  - `tool-result`：工具结果
  - `done`：token/耗时
  - `error`：错误信息
- 支持 AbortSignal 停止生成。
- 错误响应体可能是 JSON `{message}`。

## 对话状态

`src/App.tsx`：

- 先乐观插入用户消息和空助手消息。
- 首个 delta 前显示 typing。
- delta 追加内容。
- tool 事件添加工具 chip，tool-result 按同名最近一次更新状态。
- done/error/finally 清理 pending。
- 停止生成用 AbortController。
- 组件卸载时 abort。
- 新消息滚动到底部。
- 会话历史按 `用户名 + agentId` 存入 localStorage，不同智能体隔离。
- sessionId 用于续聊；后端会话消息可回读。

## 登录/目录

- 登录页调用 `/auth/login`，保存 token + user。
- 目录页展示智能体名称、团队、简介、模型、工具数、知识库数。
- 只显示后端返回的可用智能体，不在前端伪造权限。
- 退出清除 session storage。

## 样式

- `styles.css` 是独立的用户端设计；不要复用管理端 Ant Design。
- 移动端优先，输入框/消息/工具 chip 不溢出。
- 流式打字、按钮 disabled、错误 banner 清晰。
- 深色模式是否跟随系统按当前实现检查；不要引入管理端 token 依赖。

## 新增功能

1. 后端先加 `/api/v1/user/*` 接口，并确保只暴露用户可见字段。
2. `api.ts` 加类型/函数，保持统一响应解析。
3. `App.tsx` 加状态/交互。
4. SSE 新事件在 `StreamHandlers` 和 `dispatch` 增加。
5. 处理 loading/error/empty/abort。
6. `npm run build`。
7. 真实登录 + 流式对话验证。

## 常见问题

- 使用 EventSource 导致 401。
- SSE 按 chunk 直接 JSON 解析，跨块失败 → 必须缓冲按 `\n\n`。
- `delta` 当 JSON 解析 → delta 是纯文本。
- 停止生成后仍更新消息 → 检查 AbortSignal。
- 会话串到其他智能体 → storage key 必须包含用户名+agentId。
- 只在前端按状态过滤，后端未校验。
- 移动端输入框被底部安全区遮挡。

## 验证

```bash
cd user-web
npm run build
npm run dev
```

打开 5174，登录后选择智能体，验证流式、停止、错误、历史、移动端。
