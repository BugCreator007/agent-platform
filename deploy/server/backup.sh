#!/usr/bin/env bash
# 单机备份脚本：MySQL 逻辑备份 + 知识库文件卷归档 + 保留策略
# 用法：./backup.sh            # 正常备份
#       BACKUP_ROOT=/path ./backup.sh
# 建议通过 cron 每日凌晨低峰执行；失败会写入 backup.log 并返回非 0。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="$ROOT/deploy/server/docker-compose.yml"
ENV_FILE="$ROOT/deploy/server/.env"
BACKUP_ROOT="${BACKUP_ROOT:-/home/ubuntu/backups/agent-platform}"
KEEP_DAYS="${KEEP_DAYS:-7}"
FILES_VOLUME="${FILES_VOLUME:-agent-platform_backend-files}"
ALPINE_IMAGE="${ALPINE_IMAGE:-alpine:latest}"
STAMP="$(date +%Y%m%d-%H%M%S)"
DEST="$BACKUP_ROOT/$STAMP"
LOG="$BACKUP_ROOT/backup.log"

mkdir -p "$BACKUP_ROOT" "$DEST"
exec 9>"$BACKUP_ROOT/.backup.lock"
if ! flock -n 9; then
  echo "$(date '+%F %T') skip: previous backup still running" >> "$LOG"
  exit 0
fi

log() { echo "$(date '+%F %T') $*" >> "$LOG"; }
trap 'log "failed: $STAMP"; rm -rf "$DEST"' ERR

if [ ! -f "$ENV_FILE" ]; then
  echo "缺少 $ENV_FILE" >&2
  exit 1
fi
# shellcheck disable=SC1090
set -a; source "$ENV_FILE"; set +a

free_kb=$(df -Pk "$BACKUP_ROOT" | awk 'NR==2 {print $4}')
if [ "${free_kb:-0}" -lt 2097152 ]; then
  log "warn: free space < 2GB before backup ($((free_kb/1024)) MB)"
fi

log "start: $DEST"

# 1) MySQL 逻辑备份：单事务一致性快照，包含例程与触发器
cd "$ROOT"
docker compose -f "$COMPOSE_FILE" exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_PASSWORD" mysqldump -u"$MYSQL_USER" --single-transaction --quick --routines --triggers --no-tablespaces agent_platform' \
  | gzip > "$DEST/mysql.sql.gz"
if [ ! -s "$DEST/mysql.sql.gz" ]; then
  echo "mysqldump 输出为空" >&2
  exit 1
fi

# 2) 知识库原始文件卷归档（只读挂载，避免影响运行中的容器）
docker run --rm -v "$FILES_VOLUME:/data:ro" -v "$DEST:/backup" "$ALPINE_IMAGE" \
  tar czf /backup/files.tar.gz -C /data .

# 3) 记录校验和，恢复前可验证完整性
( cd "$DEST" && sha256sum mysql.sql.gz files.tar.gz > checksums.txt )

# 4) 保留策略：只删除本脚本产生的日期目录
find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -name '20*' -mtime +"$KEEP_DAYS" -exec rm -rf {} +

size=$(du -sh "$DEST" | awk '{print $1}')
log "ok: $DEST ($size)"
