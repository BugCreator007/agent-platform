#!/usr/bin/env bash
# 本地启动后端：读取 .env、打包并后台运行；日志写入 /tmp/agent-backend.log
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# 加载本地环境变量（数据库、Redis、DeepSeek Key 等）
if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

if [ -z "${DEEPSEEK_API_KEY:-}" ] || [[ "${DEEPSEEK_API_KEY}" == *"请替换"* ]] || [[ "${DEEPSEEK_API_KEY}" == "你的_key" ]]; then
  echo "提示：DEEPSEEK_API_KEY 未配置或仍是占位符，后端将使用本地演示模型。"
fi

export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export MILVUS_ENABLED="${MILVUS_ENABLED:-false}"
export ES_ENABLED="${ES_ENABLED:-false}"

cd "$ROOT/backend"
mvn -q -DskipTests package
# 先停掉占用 8081 的旧进程，避免端口冲突
OLD_PID="$(lsof -t -iTCP:8081 -sTCP:LISTEN | head -1 || true)"
if [ -n "$OLD_PID" ]; then
  kill "$OLD_PID" 2>/dev/null || true
  sleep 1
fi
nohup "$JAVA_HOME/bin/java" -jar target/agent-backend-0.1.0-SNAPSHOT.jar >/tmp/agent-backend.log 2>&1 &
echo "backend started, pid=$!"
echo "log: /tmp/agent-backend.log"
