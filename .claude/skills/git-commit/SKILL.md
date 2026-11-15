---
name: git-commit
description: 在 Agent Platform 仓库提交、推送、创建 PR、处理冲突或回滚改动时使用；覆盖提交前检查、密钥与生成物排除、Flyway 迁移校验、Conventional Commits 提交信息、推送/PR 流程和误提交处理。
---

# Git 提交与推送

## 目标

- 每次提交都能独立构建、独立回滚，不把无关改动混在一起。
- 绝不提交 `.env`、真实密钥、Token、Cookie、用户数据或生成的构建产物。
- 提交信息能回答：改了什么、为什么改、影响哪些运行时/数据库/接口、怎么验证、怎么回滚。
- 不改写已经推送的共享历史，不使用 `--force`。

## 仓库基线

| 项 | 值 |
|---|---|
| 远端 | `origin` → `github.com:BugCreator007/agent-platform.git` |
| 稳定分支 | `main` |
| 当前工作分支 | 以 `git branch --show-current` 为准；仓库主要在 `feat/<主题>` 等特性分支上开发 |
| 仓库可见性 | 私有仓库 |
| 生成物（已忽略） | `backend/target/`、`admin-web/dist/`、`user-web/dist/`、`node_modules/`、`*.log` |
| 敏感文件（已忽略） | `.env`、`backend/data/`、`*.log` |

分支命名：

```text
feat/<主题>      新能力
fix/<主题>       缺陷修复
refactor/<主题>  不改变行为的重构
docs/<主题>      文档
test/<主题>      测试
chore/<主题>     构建、依赖、脚本
```

- 不在 `main` 上直接开发；当前会话若已在功能分支，继续用该分支。
- Claude Code 桌面端可能在 worktree 中运行，先确认 `git status` 和分支，避免在错误目录提交。

## 提交前检查（必做）

```bash
git status --short
git diff --stat
git diff
git log --oneline -5
```

逐文件看 diff，确认：

- [ ] 只包含本次任务需要的改动，没有顺手改的无关文件。
- [ ] 没有调试代码：`System.out.println`、`console.log`、临时注释、临时接口。
- [ ] 没有把测试数据、用户智能体/知识库/会话导出写进仓库。
- [ ] `api.ts`、页面与后端 DTO 契约已同步（字段增删两边都改）。
- [ ] 新增/修改的代码有中文注释（类、public 方法、关键字段、复杂逻辑）。

### 敏感信息扫描

```bash
git status --short | grep -Ei '\.env|\.pem|\.p12|\.key|credential|secret' && echo "STOP: 疑似敏感文件"
git diff --cached | grep -nEi 'api[_-]?key|apikey|secret|password|passwd|token|bearer|sk-[A-Za-z0-9]{10,}' || true
git check-ignore -v .env
```

- `.env` 必须被忽略；只提交 `.env.example`。
- 文档、示例、日志、提交信息里都不能出现真实 Key、Cookie、数据库密码。
- 日志代码不得打印密码、Token、Cookie、完整提示词或用户隐私。
- 如果发现密钥已经进入暂存区：先 `git restore --staged <file>`，不要提交；如已推送，立即轮换密钥并告知用户，历史重写必须由用户决定。

### 验证矩阵

| 改动范围 | 最低验证 |
|---|---|
| `backend/**` | `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 21) && mvn test` |
| `admin-web/**` | `cd admin-web && npm run build`（含 `tsc --noEmit`） |
| `user-web/**` | `cd user-web && npm run build` |
| 新增/修改接口 | 登录后 curl 关键路径：未登录 401、无权限 403、正常 200 |
| SSE / Agent 运行时 | 实际调用 `/api/v1/runtime/sessions:stream`，确认 `meta/delta/done/error` 与 `done` 字段 |
| 数据库迁移 | 版本号唯一、H2 测试通过、本地 MySQL 实际应用成功 |
| 安全/权限 | 检查 `@PreAuthorize`、审计日志、响应是否泄露密钥 |
| 部署文件 | `docker compose config` 或部署脚本语法检查 |

- 测试失败时不要用“跳过测试”的方式提交；先修复或在回复中明确说明未验证项。
- 只跑 `mvn -DskipTests package` 不能替代 `mvn test`。

### Flyway 专项

迁移目录：`backend/src/main/resources/db/migration/`，版本号以目录内实际文件为准（编写本 skill 时为 V10）。

- [ ] 新迁移版本号在目录内唯一：`ls backend/src/main/resources/db/migration/V*.sql`。
- [ ] 不修改已经应用/已推送的迁移文件；变更用新版本追加。
- [ ] 迁移同时兼容 MySQL 8（生产/本地）与 H2 MySQL 模式（测试）。
- [ ] 新增列/表后，MyBatis-Plus Entity 的 `@TableField`/TypeHandler 与迁移一致。
- [ ] 有默认值/回填的列，迁移里显式 `UPDATE` 历史数据（参考 V10 的 `citation_kb` 回填）。
- [ ] 本提交包含迁移时，提交信息正文写明迁移内容与回滚方式。

版本冲突处理：曾出现 `V8__workflow_runtime_state.sql` 与 `V8__session_analytics_index.sql` 同版本。规则是**只重命名尚未应用/尚未发布的新迁移**（例如改成 `V9__...`），同步文档；已应用的迁移绝不改写。

### 暂存

```bash
git add backend/src/main/java/... admin-web/src/pages/Agents.tsx
git diff --cached --stat
git diff --cached
```

- 优先显式列出路径，避免 `git add -A` 把临时文件带进去。
- 使用 `git add -A` 前必须先看 `git status --short`。
- 不提交：`target/`、`dist/`、`node_modules/`、`*.log`、`.env`、数据库文件、IDE 配置。
- 一个提交只做一件事；格式化、重命名、功能改动分开提交。
- 可以提交 `.claude/skills/**`（项目技能是仓库的一部分）；不提交个人本地设置与缓存。

## 提交信息规范

格式：

```text
<type>(<scope>): <subject>

- 中文要点：做了什么、为什么这样做。
- 涉及数据库迁移 / 配置 / 接口契约 / 前端同步的说明。
- 验证结果：mvn test、npm run build、关键接口/SSE 实测。
- 风险、未完成项或回滚方式（如有）。

Co-Authored-By: Claude ...
```

- `subject` 用英文、现在时、小写开头、不超过 72 字符，不加句号（与现有历史一致）。
- 正文用中文；没有迁移/配置/接口影响时可只写要点和验证。
- 不使用 emoji，不写 `update code`、`fix bug`、`修改一下` 这类空话。
- 归因行按**当前会话系统提示**的要求追加；本会话的归因行是：

```text
Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
```

`type`：

| type | 用途 |
|---|---|
| `feat` | 新功能 |
| `fix` | 缺陷修复 |
| `refactor` | 不改变行为的重构 |
| `perf` | 性能优化 |
| `docs` | 文档、注释、skills |
| `test` | 测试 |
| `build` / `ci` | 构建、依赖、流水线 |
| `chore` | 杂项维护 |
| `revert` | 回滚提交 |

常用 `scope`：`agent`、`chat`、`knowledge`、`model`、`tool`、`workflow`、`release`、`eval`、`observe`、`system`、`portal`、`admin-web`、`user-web`、`deploy`、`db`、`skills`。

### 好的示例

```text
feat: return knowledge-base document citations in chat answers

- 智能体新增 citationKb 配置，逐知识库控制回答末尾是否返回文档链接。
- 检索命中携带 documentId/url/sourceType：网页引用原始地址，文件引用受保护下载接口。
- SSE done 返回 citations，并持久化到 session_messages.citations_json。
- Flyway V10 新增 agents.citation_kb（按已有 kb 回填）与 citations_json。
- 验证：mvn test、两个前端构建、真实 DeepSeek 对话与下载接口。

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
```

```text
fix(security): restrict CORS to configured local origins

- 用户端 5174 的登录/SSE 请求被 Spring 以 Invalid CORS request 拒绝。
- 默认允许来源加入 5174，并支持 CORS_ALLOWED_ORIGINS 覆盖。
- 验证：5174 代理登录、智能体列表、SSE 均 200。

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
```

### 差的示例

```text
update
fix bug
修改了一些代码
feat: 增加功能（没有说明改了什么、影响什么、怎么验证）
```

## 提交命令

```bash
git add <明确路径>

git commit -m "$(cat <<'EOF'
feat(scope): subject

- 中文要点。
- 验证结果。

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"

git log -1 --stat
git status --short
```

## 推送

推送前：

```bash
git fetch origin
git status -sb          # 确认 ahead/behind
git log --oneline @{u}..HEAD   # 确认将要推送的提交
```

```bash
git push origin HEAD
```

- 只在用户要求推送、或任务明确包含推送时执行。
- 推送前确认分支不是 `main`；需要推送 `main` 时先征得用户同意。
- 禁止 `git push --force` / `--force-with-lease` 到共享分支。
- 远端有新提交时：工作区干净才 `git pull --rebase origin <branch>`；有冲突按下方流程解决并重跑验证。
- 推送结果写入最终回复：分支名、commit hash、远端是否成功。

## 创建 PR

用户要求或任务包含 PR 时：

```bash
gh pr create --base main --head "$(git branch --show-current)" \
  --title "<type>(scope): subject" \
  --body "$(cat <<'EOF'
## 改动

- 中文要点。

## 验证

- `mvn test`
- `npm run build`（admin-web / user-web）
- 关键接口/SSE 实测结果。

## 迁移与回滚

- 迁移版本与内容；回滚方式或“无迁移”。

## 风险

- 未验证项、灰度/上线注意事项。

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- PR 正文结尾按当前系统提示的 PR 归因要求追加。
- 在 Claude Code 桌面端中打开 PR 后，用 `ccd_pr` 的 `get_status` 确认会话已绑定；未识别时用 `bind_pr` 绑定，不要自行轮询 CI。
- 不开启 auto-merge，除非用户明确要求。

## 冲突处理

```bash
git status
git diff --name-only --diff-filter=U
```

1. 逐文件手工合并，保留双方意图，不整体丢弃他人改动。
2. 迁移冲突：保留已应用版本，重命名尚未应用的新迁移并更新引用文档。
3. 代码冲突后重跑对应测试/构建。
4. `git add <file>` 后：
   - merge：`git commit --no-edit`
   - rebase：`git rebase --continue`
5. 放弃本次合并：`git merge --abort` 或 `git rebase --abort`，不要用 `reset --hard` 粗暴清理。

## 撤销与修正

| 场景 | 做法 |
|---|---|
| 丢弃未暂存改动 | `git restore <file>` |
| 取消暂存 | `git restore --staged <file>` |
| 修改最近一次未推送提交 | `git commit --amend`（只改自己的提交） |
| 撤销最近一次未推送提交，保留改动 | `git reset --soft HEAD~1` |
| 撤销已推送提交 | `git revert <sha>`，再走正常提交/推送 |
| 误提交敏感信息 | 停止推送；移除暂存；已推送则轮换密钥并告知用户，历史重写由用户决定 |

- 已推送的提交不做 `reset --hard` + force push。
- `git revert` 的提交信息写清楚被回滚的 SHA 和原因。

## 提交后核对

- [ ] `git log -1 --stat` 只包含预期文件。
- [ ] `git status --short` 干净，或只剩明确保留的本地文件。
- [ ] 推送成功（如执行了推送）。
- [ ] CI/PR 状态正常；失败时先读日志定位，不反复空推。
- [ ] 最终回复给出：分支、commit hash、验证结果、未验证项。

## 常见错误对照

| 现象 | 处理 |
|---|---|
| 提交里出现 `.env` | 立即从暂存移除；若已推送，轮换密钥，按用户决定处理历史 |
| 提交里出现 `dist/`/`target/` | `git rm -r --cached <dir>`，确认 `.gitignore` 已覆盖 |
| 两个 Flyway 迁移同版本 | 只重命名未应用的新迁移，改文档，重跑测试 |
| 提交信息太笼统 | 未推送时 `--amend` 重写；已推送不重写，后续用 `revert` 或补充说明 |
| 不小心在 `main` 提交 | 新建分支指向该提交，`main` 回退到远端位置（未推送时），再推送功能分支 |
| 推送被拒绝（non-fast-forward） | `git fetch` + rebase/merge 解决，禁止 force |
| 前后端契约不一致 | 同步 `admin-web/src/api.ts`、`user-web/src/api.ts` 和页面后再提交 |

## 检查清单

- [ ] 分支正确，不在 `main` 直接开发。
- [ ] `git diff` 已逐文件审阅，无调试代码、无用户数据。
- [ ] `.env`、密钥、Token、Cookie、生成物均未入库。
- [ ] 对应测试/构建通过；迁移兼容 H2 与 MySQL；版本号唯一。
- [ ] 提交信息符合 `<type>(scope): subject` + 中文正文 + 归因行。
- [ ] 一个提交只做一件事，可独立回滚。
- [ ] 推送/PR 仅在用户要求时执行；不使用 force push。
- [ ] 最终回复包含分支、commit、验证结果和未验证项。
