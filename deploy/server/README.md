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

使用 `deploy/server/backup.sh` 做逻辑备份（单事务一致性）和文件卷归档：

```bash
./deploy/server/backup.sh
```

默认输出到 `/home/ubuntu/backups/agent-platform/<时间戳>/`，包含：

- `mysql.sql.gz`：`mysqldump --single-transaction --no-tablespaces`
- `files.tar.gz`：`backend-files` 卷只读归档
- `checksums.txt`：完整性校验
- 默认保留 7 天，日志写入 `backup.log`；可用 `BACKUP_ROOT`、`KEEP_DAYS` 覆盖。

定时执行（安装 cron 后添加，注意不要与其它任务同时段）：

```bash
crontab -e
17 3 * * * /home/ubuntu/agent-platform/deploy/server/backup.sh >/dev/null 2>&1
```

恢复演练建议：先建临时库导入 `mysql.sql.gz` 验证表数与行数，再在维护窗口停后端做正式恢复；
不要使用 `docker compose down -v`，会删除数据卷。

## 回滚

代码回滚后重新执行 `deploy.sh` 即可；数据库迁移向前兼容，不自动回滚。
镜像 tag 由 compose 本地构建，旧镜像仍保留在服务器上，可用 `docker images` 查看。
