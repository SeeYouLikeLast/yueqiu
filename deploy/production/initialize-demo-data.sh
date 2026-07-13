#!/usr/bin/env bash
set -euo pipefail

# 仅用于全新服务器的首次演示数据初始化。
# schema.sql 会重建表结构，data.sql 会写入轻量演示数据，因此已有真实数据时绝不能再次执行。
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ENV_FILE="${SCRIPT_DIR}/.env"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "缺少 ${ENV_FILE}，请先从 .env.example 复制并配置密码。" >&2
  exit 1
fi

cd "${SCRIPT_DIR}"
echo "将重建 hm_badminton 的全部表并写入演示数据。输入 YES 继续："
read -r confirmation
if [[ "${confirmation}" != "YES" ]]; then
  echo "已取消。"
  exit 0
fi

docker compose --env-file "${ENV_FILE}" exec -T mysql sh -lc \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
  < "${PROJECT_ROOT}/backend/src/main/resources/db/schema.sql"

docker compose --env-file "${ENV_FILE}" exec -T mysql sh -lc \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
  < "${PROJECT_ROOT}/backend/src/main/resources/db/data.sql"

echo "数据库演示数据初始化完成。接下来运行 ./upload-demo-assets.sh 上传 MinIO 图片。"
