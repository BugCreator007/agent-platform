# 单台腾讯云 CVM 部署

适用于一台 CVM（2C4G 及以上），用 Docker Compose 起 MySQL、Redis、后端和前端。
Milvus / Elasticsearch 默认关闭，需要时再在 `deploy/server/.env` 打开。

## 首次部署

1. 准备环境变量（已由本地 `.env` 生成，含随机数据库密码、JWT 密钥和 DeepSeek Key）：

   ```bash
   cp deploy/server/.env.example deploy/server/.env
   # 替换所有 change-me，或从本地 .env 复制 DEEPSEEK_API_KEY
   chmod 600 deploy/server/.env
   ```

2. 服务器安装 Docker（Ubuntu）：

   ```bash
   sudo apt-get update && sudo apt-get install -y docker.io docker-compose-v2
   sudo systemctl enable --now docker
   ```

3. 部署：

   ```bash
   ./deploy/server/deploy.sh root@<服务器公网IP>
   ```

   脚本会 rsync 代码到 `/opt/agent-platform`，远端执行 `docker compose up -d --build`，并轮询后端健康检查。

4. 访问 `http://<服务器公网IP>/`，演示账号 `admin / admin123`。

## 常用运维

```bash
cd /opt/agent-platform
docker compose -f deploy/server/docker-compose.yml ps
docker compose -f deploy/server/docker-compose.yml logs -f agent-backend
docker compose -f deploy/server/docker-compose.yml restart agent-backend
docker compose -f deploy/server/docker-compose.yml down      # 停止，保留数据卷
```

## 数据与备份

- MySQL 数据：卷 `mysql-data`
- Redis 数据：卷 `redis-data`
- 知识库文件：卷 `backend-files`

```bash
docker run --rm -v agent-platform_mysql-data:/data -v "$PWD":/backup alpine \
  tar czf /backup/mysql-$(date +%F).tar.gz -C /data .
```

## 回滚

代码回滚后重新执行 `deploy.sh` 即可；数据库迁移向前兼容，不自动回滚。
镜像 tag 由 compose 本地构建，旧镜像仍保留在服务器上，可用 `docker images` 查看。
