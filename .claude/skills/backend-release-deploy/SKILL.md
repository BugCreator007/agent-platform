---
name: backend-release-deploy
description: 构建、Docker 镜像、Docker Compose、单台腾讯云 CVM 发布、TKE/K8s 清单、环境变量、健康检查、回滚和备份时使用。
---

# 发布与部署

## 构建

```bash
# 后端
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn -DskipTests package
# 或完整测试
mvn test

# 管理端
cd ../admin-web
npm ci
npm run build

# 用户端
cd ../user-web
npm ci
npm run build
```

产物：

- `backend/target/agent-backend-0.1.0-SNAPSHOT.jar`
- `admin-web/dist`
- `user-web/dist`

## 本地/单机 Docker Compose

- 通用：`deploy/docker-compose.yml`
- 单台腾讯云 CVM：`deploy/server/docker-compose.yml`
- 服务：MySQL、Redis、agent-backend、agent-frontend、user-web。
- 管理端默认宿主机 `HTTP_PORT=80`；用户端 `USER_HTTP_PORT=8089`。
- MySQL/Redis 只在容器网络内，不暴露宿主机。
- 数据卷：`mysql-data`、`redis-data`、`backend-files`。

## 单台腾讯云 CVM 发布

脚本：

```bash
./deploy/server/deploy.sh root@<服务器公网IP>
# 或
REMOTE_DIR=/home/ubuntu/agent-platform \
SSH_KEY=~/Downloads/lu.pem \
./deploy/server/deploy.sh ubuntu@62.234.140.216
```

脚本行为：

1. 检查 `deploy/server/.env`。
2. rsync 源码到远端 `REMOTE_DIR`（排除 `.git/.idea/.claude/.env/target/node_modules/dist`）。
3. 远端依次构建 backend/admin/user，避免 2C 机器内存峰值。
4. `docker compose up -d`。
5. 通过 agent-frontend 容器访问 backend health，轮询 5 分钟。

### 当前服务器事实（来自项目 memory）

- 腾讯云 CVM `62.234.140.216`，Ubuntu 24.04，2C/2G。
- 代码目录 `/home/ubuntu/agent-platform`。
- 管理端发布在宿主机 **8088**；80/8081 被既有 Caddy/filebox 占用。
- 安全组只放行 80/8081/22；8088 需在腾讯云控制台加规则。
- 不要修改 `/etc/caddy/Caddyfile` 原有 site block。
- 已有 swap `/swap2.img`。
- Docker 镜像源已配置腾讯云 mirror。

## 环境变量

`deploy/server/.env` 不入库，至少包含：

```text
DB_USERNAME/DB_PASSWORD/DB_ROOT_PASSWORD
JWT_SECRET
SECRETS_KEY
BOOTSTRAP_ADMIN_PASSWORD
DEMO_SEED
DEEPSEEK_API_KEY
DEEPSEEK_BASE_URL/MODEL
EMBEDDING_*
MILVUS_ENABLED/URL
ES_ENABLED/URL
STORAGE_TYPE/LOCAL_ROOT
CORS_ALLOWED_ORIGINS
HTTP_PORT/USER_HTTP_PORT
```

- `SECRETS_KEY` 变更会让已存 Provider/MCP 密钥失效。
- `JWT_SECRET` 至少 32 字节。
- 生产 CORS 使用实际域名。
- 不提交 `.env`；服务器 `chmod 600`。

## K8s / TKE

- `deploy/k8s/namespace.yaml`
- `configmap.yaml`：非敏感配置
- `secret.example.yaml`：DB/JWT/Key 示例
- `backend.yaml`：3 副本、滚动更新、探针
- `frontend.yaml`：Nginx 管理端
- `hpa.yaml`：CPU 65% 扩缩 3–20
- `ingress.yaml`：CLB/Ingress `/api` 转发
- 需要替换镜像地址、域名、LB ID、TLS Secret。
- 当前清单未在真实 TKE 联调。

## 健康检查与日志

```bash
curl -s http://localhost:8081/actuator/health
docker compose -f deploy/server/docker-compose.yml ps
docker compose -f deploy/server/docker-compose.yml logs -f agent-backend
```

本地日志：

- `/tmp/agent-backend.log`
- `/tmp/admin-web.log`
- `/tmp/user-web.log`

## 回滚

1. 切回上一个 Git commit/tag。
2. 重新执行 `deploy.sh` 或 build/up。
3. 数据库迁移默认向前兼容，不自动 down；回滚前确认迁移可兼容旧代码。
4. 保留旧镜像，必要时用上一镜像启动。
5. 数据卷不随 down 删除；不要 `down -v`。

## 备份

```bash
docker run --rm -v agent-platform_mysql-data:/data -v "$PWD":/backup alpine \
  tar czf /backup/mysql-$(date +%F).tar.gz -C /data .
```

知识库文件卷 `backend-files` 同样备份。

## 发布前 Checklist

- [ ] `mvn test` 通过。
- [ ] admin-web/user-web `npm run build` 通过。
- [ ] 新迁移版本唯一，H2/MySQL 都可执行。
- [ ] `.env` 未被提交。
- [ ] 新配置加入 `.env.example`/deploy README。
- [ ] CORS/域名/端口正确。
- [ ] 生产不启用演示种子（`DEMO_SEED=false`）。
- [ ] 健康检查、登录、关键接口验证。
- [ ] 备份和回滚步骤明确。

## 常见故障

- 80 被 Caddy 占用 → 使用 `HTTP_PORT=8088` 或修改端口，不动 Caddy。
- DB 连接拒绝 → 检查容器状态、DB_URL、引号、启动顺序。
- 迁移 duplicate version → 合并/重命名迁移。
- 2G 内存 OOM → 顺序构建、限制 JVM/MySQL 内存、swap。
- 前端 404 → Nginx `/api` 代理或 Ingress 路径错误。
- 密钥解密失败 → `SECRETS_KEY` 变了。
