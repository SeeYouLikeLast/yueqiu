#!/usr/bin/env bash
set -euo pipefail

# 将仓库中的轻量演示图片上传到本机 MinIO。
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ENV_FILE="${SCRIPT_DIR}/.env"
ASSETS_DIR="${PROJECT_ROOT}/seed-assets/generated"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "缺少 ${ENV_FILE}，请先从 .env.example 复制并配置密码。" >&2
  exit 1
fi
if [[ ! -d "${ASSETS_DIR}/demo" ]]; then
  echo "未找到 ${ASSETS_DIR}/demo，仓库可能未完整上传。" >&2
  exit 1
fi

set -a
source "${ENV_FILE}"
set +a

docker run --rm \
  --network container:hm-badminton-minio \
  --volume "${ASSETS_DIR}:/assets:ro" \
  --env MINIO_ROOT_USER \
  --env MINIO_ROOT_PASSWORD \
  --env MINIO_BUCKET \
  --entrypoint /bin/sh \
  "${MINIO_CLIENT_IMAGE:-quay.io/minio/mc:latest}" \
  -c '
    mc alias set local http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
    mc mb --ignore-existing "local/$MINIO_BUCKET"
    mc anonymous set download "local/$MINIO_BUCKET"
    mc cp --recursive /assets/demo "local/$MINIO_BUCKET/"
  '

echo "MinIO 演示图片上传完成。"
