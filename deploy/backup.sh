#!/bin/bash
# DB 전체를 덤프해 S3(ormo-db-backup-*/dev/)에 올린다. cron으로 매일 새벽 4시 30분(KST) 실행. 30일 뒤 자동 삭제.
#   sudo /opt/ormo/Backend/deploy/backup.sh
set -euo pipefail
cd "$(dirname "$0")"
BUCKET="ormo-db-backup-$(aws sts get-caller-identity --query Account --output text)"
FILE="coffeul-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --routines --triggers --set-gtid-purged=OFF --databases coffeul' | gzip > "$TMP/$FILE"
[ "$(stat -c %s "$TMP/$FILE")" -gt 1000 ] || { echo "[backup] 덤프가 비정상적으로 작다" >&2; exit 1; }
aws s3 cp --only-show-errors "$TMP/$FILE" "s3://$BUCKET/dev/$FILE"
echo "[backup] s3://$BUCKET/dev/$FILE ($(stat -c %s "$TMP/$FILE") bytes)"
