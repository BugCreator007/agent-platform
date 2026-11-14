#!/usr/bin/env bash
# 单台腾讯云 CVM 部署：rsync 代码后远端 docker compose 构建启动
# 用法：./deploy.sh <user@host> [ssh端口]
set -euo pipefail

SERVER="${1:?用法: ./deploy.sh user@host [ssh端口]}"
PORT="${2:-22}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REMOTE_DIR="${REMOTE_DIR:-/opt/agent-platform}"
COMPOSE="deploy/server/docker-compose.yml"
SSH_KEY="${SSH_KEY:-}"

SSH=(ssh -p "$PORT")
RSYNC_SSH="ssh -p $PORT"
if [ -n "$SSH_KEY" ]; then
  SSH+=(-i "$SSH_KEY")
  RSYNC_SSH="ssh -p $PORT -i $SSH_KEY"
fi

if [ ! -f "$ROOT/deploy/server/.env" ]; then
  echo "缺少 deploy/server/.env，请先复制 .env.example 并填写真实值" >&2
  exit 1
fi

if ! "${SSH[@]}" -o BatchMode=yes "$SERVER" "command -v docker >/dev/null && docker compose version >/dev/null"; then
  echo "远端缺少 docker 或 compose 插件，请先在服务器安装 Docker" >&2
  exit 1
fi

echo "==> 同步代码到 $SERVER:$REMOTE_DIR"
"${SSH[@]}" "$SERVER" "mkdir -p '$REMOTE_DIR'"
rsync -az --delete -e "$RSYNC_SSH" \
  --exclude '.git' --exclude '.idea' --exclude '.claude' --exclude '/.env' \
  --exclude 'backend/target' --exclude 'backend/data' \
  --exclude 'frontend/node_modules' --exclude 'frontend/dist' \
  --exclude 'frontend/.vite' --exclude '.DS_Store' --exclude '*.log' \
  --exclude 'data' \
  "$ROOT/" "$SERVER:$REMOTE_DIR/"

echo "==> 远端构建并启动"
# 低配机器按顺序构建，避免 Maven/npm 同时占用内存
"${SSH[@]}" "$SERVER" "cd '$REMOTE_DIR' && docker compose -f $COMPOSE build agent-backend && docker compose -f $COMPOSE build agent-frontend && docker compose -f $COMPOSE up -d"

echo "==> 等待后端就绪"
for i in $(seq 1 60); do
  if "${SSH[@]}" "$SERVER" \
    "cd '$REMOTE_DIR' && docker compose -f $COMPOSE exec -T agent-frontend wget -qO- http://agent-backend:8081/actuator/health 2>/dev/null | grep -q '\"status\":\"UP\"'"; then
    echo "后端健康检查通过"
    "${SSH[@]}" "$SERVER" "cd '$REMOTE_DIR' && docker compose -f $COMPOSE ps"
    exit 0
  fi
  sleep 5
done

echo "后端 5 分钟内未就绪，最近日志：" >&2
"${SSH[@]}" "$SERVER" "cd '$REMOTE_DIR' && docker compose -f $COMPOSE logs --tail=80 agent-backend" >&2
exit 1
