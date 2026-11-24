#!/bin/bash
# 직전에 성공한 이미지로 되돌린다. DB 스키마는 되돌리지 않는다(Flyway는 앞으로만 간다).
#   sudo /opt/ormo/Backend/deploy/rollback.sh [image-tag]
set -euo pipefail
cd "$(dirname "$0")"
TAG="${1:-$(cat .previous-tag)}"
docker image inspect "ormo-backend:$TAG" >/dev/null
sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=$TAG/" .env
docker compose up -d --no-build
echo "$TAG" > .current-tag
echo "[rollback] ormo-backend:$TAG 로 되돌렸다."
