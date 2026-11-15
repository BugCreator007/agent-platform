---
name: local-development
description: 本地启动/重启 Agent Platform 后端、admin-web、user-web、MySQL/Redis/ES/Milvus，排查本地端口、数据库、Flyway、依赖和代理问题时使用。
---

# 本地开发与启动

## 目录

```text
/Users/lu/面试/agent-platform
  backend
  admin-web
  user-web
  deploy
  scripts/run-backend.sh
  Makefile
```

## 端口

| 服务 | 地址 |
|---|---|
| 管理后台 | http://localhost:5173 |
| 用户端 | http://localhost:5174 |
| 后端 | http://localhost:8081 |
| Swagger | http://localhost:8081/swagger-ui.html |
| MySQL | localhost:3308（当前本地 .env） |
| Redis | localhost:6379 |
| Elasticsearch | localhost:9200 |
| Milvus | localhost:19530 |

登录：`admin/admin123`、`developer/developer123`、`viewer/viewer123`。

## 依赖容器

```bash
docker start agent-mysql-local agent-es rag-kb-milvus
# 如果需要重建/查看
docker ps --format '{{.Names}}\t{{.Status}}\t{{.Ports}}'
```

- `agent-mysql-local`：MySQL 8.4，宿主 3308 → 3306。
- `rag-kb-milvus`：Milvus。
- `agent-es`：Elasticsearch。
- Redis 可能由本机/容器提供，确认 6379。

MySQL 验证：

```bash
docker exec -e MYSQL_PWD=agent123 agent-mysql-local mysql -uagent -e 'SELECT 1'
```

## 后端启动

```bash
cd /Users/lu/面试/agent-platform
./scripts/run-backend.sh
```

脚本会：

1. source `.env`。
2. `mvn -q -DskipTests package`。
3. 杀掉占用 8081 的旧进程。
4. nohup 启动 jar，日志 `/tmp/agent-backend.log`。

或开发模式：

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn spring-boot:run
```

健康检查：

```bash
curl -s http://localhost:8081/actuator/health
```

## 管理端

```bash
cd admin-web
npm install   # 首次
npm run dev   # 5173
npm run build
```

Vite 代理 `/api` → 8081。

## 用户端

```bash
cd user-web
npm install
npm run dev   # 5174
npm run build
```

可用 `VITE_PROXY_TARGET` 覆盖后端地址。

## Makefile

```bash
make backend    # mvn spring-boot:run
make admin-web  # npm install + dev
make user-web   # npm install + dev
make build      # 后端 jar + 两个前端 dist
make test       # mvn test
```

## 常见问题

### DB 连接拒绝

- MySQL 未启动：`docker start agent-mysql-local`。
- `.env` 的 `DB_URL` 含 `&` 未加引号：必须写成
  `DB_URL='jdbc:mysql://...?...&...'`，否则 shell 截断。
- 端口不对：当前本地是 3308，README 旧文档可能写 3307。
- 启动顺序：先等 MySQL 可用，再启动后端；脚本没有等待依赖。

### Flyway duplicate version

- `ls backend/src/main/resources/db/migration` 检查是否两个同版本文件。
- 已应用版本不能改；把并行新增的迁移重命名为下一个版本。
- 本地数据库版本与文件冲突时，先确认数据，不要随意清库。

### 端口被占用

```bash
lsof -nP -iTCP:8081 -sTCP:LISTEN
lsof -nP -iTCP:5173 -sTCP:LISTEN
lsof -nP -iTCP:5174 -sTCP:LISTEN
```

杀掉旧进程后再启动。

### 401/CORS

- 管理端/用户端 token key 不同：`agent_platform_token`、`agent_user_token`。
- Vite 代理存在时不应有 CORS；跨域直连才检查 `CORS_ALLOWED_ORIGINS`。
- SSE 请求必须带 Bearer。

### 前端 build 失败

- 先看 `tsc --noEmit` 错误，通常是 `api.ts` 类型与页面不一致。
- 清理 `node_modules/.vite` 缓存可用 `rm -rf node_modules/.vite`（谨慎）。

### 本地数据

- 不要随意执行 `docker rm -v agent-mysql-local` 或 `down -v`。
- 测试数据清理要区分用户创建的数据。
- `.env`、`deploy/server/.env` 不入库，权限 600。

## 启动后验证

```bash
curl -s http://localhost:8081/actuator/health
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:5173/
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:5174/
```

登录、打开流程/模型/知识库/工具页面，检查后端日志无启动异常。

## 输出要求

先给可访问地址和状态，再给日志路径和失败原因；不要只说“已启动”。
