#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

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
OLD_PID="$(lsof -t -iTCP:8081 -sTCP:LISTEN | head -1 || true)"
if [ -n "$OLD_PID" ]; then
  kill "$OLD_PID" 2>/dev/null || true
  sleep 1
fi
nohup "$JAVA_HOME/bin/java" -jar target/agent-backend-0.1.0-SNAPSHOT.jar >/tmp/agent-backend.log 2>&1 &
echo "backend started, pid=$!"
echo "log: /tmp/agent-backend.log"
