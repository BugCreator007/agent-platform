---
name: frontend-admin-web
description: 修改管理后台 admin-web（React 19 + TypeScript + Vite + Ant Design + TanStack Query）页面、组件、主题、API 契约或交互时使用。
---

# 管理后台前端开发

## 技术栈

- React 19 + TypeScript + Vite。
- 端口 5173；开发代理 `/api` → `http://localhost:8081`。
- TanStack Query 管理服务端状态。
- Ant Design 中文 ConfigProvider + 自定义设计系统（`src/components/ui.tsx` + `styles.css`）。
- React Router 7。

## 目录

```text
admin-web/src/
  App.tsx               壳层、导航、登录门禁、主题、命令面板
  api.ts                API 客户端与所有接口类型
  modules.ts            导航/路由模块定义
  components/ui.tsx     Button/Panel/Modal/Drawer/Tabs/Badge/Toast/图表
  components/icons.tsx  SVG sprite
  pages/*.tsx           运营概览、智能体、模型、工具、知识库、工作流、调试等
  styles.css            设计 token、深浅色主题、页面样式
```

## API 客户端

- `api.get/post/put/postForm/putForm/del`
- 自动附加 Bearer token。
- 401（非登录）清除 token 并派发 `auth-expired`。
- 只有 `success=true` 且 HTTP 2xx 返回 `data`，否则抛后端 message。
- 新增接口必须同步 `api.ts` 类型，不允许页面直接用 fetch（除非 SSE/特殊）。

## Query 模式

```tsx
const list = useQuery({ queryKey: ['items'], queryFn: () => api.get<Item[]>('/items') })
const create = useMutation({
  mutationFn: (body: ItemRequest) => api.post<Item>('/items', body),
  onSuccess: () => {
    toast('已创建')
    queryClient.invalidateQueries({ queryKey: ['items'] })
  },
  onError: (e: Error) => toast(e.message, 'err'),
})
```

- queryKey 规范：`['agents']`、`['workflow-instance', id]`、`['mcp-tools', serverId]`。
- 变更后 invalidate 相关 key。
- 轮询用 `refetchInterval`（工作流实例 2s），不要用 setInterval。
- 页面卸载/请求取消用 AbortController。

## 页面结构

- `PageHead`：标题、说明、操作按钮。
- KPI：`grid-4` + `panel stat`。
- 列表：`Panel` + `.table-wrap` + `table.data`。
- 详情/编辑：`Drawer` 或 `Modal`。
- 状态：`StatusBadge`、`Badge`。
- 空态：`Empty`/`.empty`。
- 错误：toast + 页面保留。
- 表格行点击打开详情，操作按钮 `stopPropagation`。

## 主题

- `useTheme()` 管理 system/light/dark，写入 `<html data-theme>` 和 localStorage `agent-theme`。
- CSS token 在 `styles.css` 的 `:root` 和 `:root[data-theme="dark"]`。
- 新样式优先用 `var(--surface/--ink/--line/--pine...)`，不要在组件里写死浅色十六进制。
- 新组件必须检查深色模式。
- 移动端 1020/760px 断点。

## 表单

- 输入用 `.input`/`.select`，字段用 `.field`。
- 必填/错误显示；提交按钮 disabled。
- 编辑弹窗关闭要重置 state。
- 危险操作 `window.confirm` 或专用确认，文案说明影响范围。
- 密钥输入 `type=password`，不回显明文；保存后只显示掩码。

## 权限

- 管理端目前主要靠后端 `@PreAuthorize` 和 `/auth/me` 角色。
- 删除/敏感操作只对 `PLATFORM_ADMIN` 显示，但后端仍是最终边界。
- 不要只隐藏按钮而不校验接口。
- 登录失效由 `auth-expired` 统一处理。

## SSE 调试页

- `Debug.tsx` 用 `fetch` + `ReadableStream`，不能用 EventSource（需 Bearer）。
- 解析 `\n\n` 事件块，处理跨 chunk。
- 处理 `meta/delta/tool/tool-result/done/error`。
- 支持停止/Abort、历史会话、模型/上下文展示。
- 工具事件显示 chip/note。

## 图表

- `Sparkline`、`LineChart` 在 `components/ui.tsx`。
- 颜色使用 CSS 变量或设计 token；深色模式检查对比度。
- 图表数据来自真实 API 时处理空数组/loading。

## 新增页面

1. `modules.ts` 加 ModuleKey/路径/分组。
2. `pages/` 新建页面并导出到 `pages/index.ts`。
3. `App.tsx` 加 Route 与 iconMap。
4. `api.ts` 加类型/调用。
5. 导航、权限、移动端检查。
6. `npm run build`。
7. 页面实际登录验证。

## 常见问题

- 后端字段变了，`api.ts` 未同步 → `tsc` 失败。
- 页面直接 fetch 绕过 token/统一错误。
- 用 `Map.of` 后端返回 null，前端未处理。
- 写死浅色背景导致深色模式不可读。
- 删除按钮没有二次确认。
- 表格操作按钮触发整行点击。
- 轮询未清理，离开页面仍请求。
- 移动端表格/侧栏溢出。

## 验证

```bash
cd admin-web
npm run build
npm run dev
```

打开 5173，登录 `admin/admin123`，检查目标页面、深色/浅色、移动端和错误态。
