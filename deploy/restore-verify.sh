#!/bin/bash
# 최신(또는 지정한) 백업을 임시 MySQL 컨테이너에 복구해 보고 원본과 테이블별 행 수를 비교한다. 운영 DB는 건드리지 않는다.
#   sudo /opt/ormo/Backend/deploy/restore-verify.sh [s3-key]
# 실제 복구는 README의 "DB 복구" 절차를 따른다.
set -euo pipefail
cd "$(dirname "$0")"
BUCKET="ormo-db-backup-$(aws sts get-caller-identity --query Account --output text)"
KEY="${1:-$(aws s3api list-objects-v2 --bucket "$BUCKET" --prefix dev/ --query 'sort_by(Contents,&LastModified)[-1].Key' --output text)}"
TMP=$(mktemp -d); trap 'rm -rf "$TMP"; docker rm -f ormo-restore-test >/dev/null 2>&1 || true' EXIT
aws s3 cp --only-show-errors "s3://$BUCKET/$KEY" "$TMP/dump.sql.gz"
docker run -d --name ormo-restore-test -e MYSQL_ROOT_PASSWORD=verify -e TZ=UTC mysql:8.4 --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci >/dev/null
for i in $(seq 1 60); do docker exec ormo-restore-test mysql -uroot -pverify -e 'select 1' >/dev/null 2>&1 && break; sleep 2; done
gunzip -c "$TMP/dump.sql.gz" | docker exec -i ormo-restore-test mysql -uroot -pverify
Q="SELECT table_name FROM information_schema.tables WHERE table_schema='coffeul' AND table_type='BASE TABLE' ORDER BY 1"
restored_q() { docker exec ormo-restore-test mysql -uroot -pverify -N -B -e "$1" 2>/dev/null; }
live_q() { docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -B -e "$1"' -- "$1" 2>/dev/null; }
counts() { for t in $($1 "$Q"); do echo "$t $($1 "SELECT COUNT(*) FROM coffeul.$t")"; done; }
counts restored_q > "$TMP/restored.txt"
counts live_q > "$TMP/live.txt"
echo "[restore-verify] 백업: $KEY"
echo "[restore-verify] 복구된 테이블 수: $(wc -l < "$TMP/restored.txt")"
if diff "$TMP/live.txt" "$TMP/restored.txt" >/dev/null; then echo "[restore-verify] 통과: 모든 테이블 행 수가 원본과 같다"; else echo "[restore-verify] 차이 있음(백업 이후 변경분일 수 있다):"; diff "$TMP/live.txt" "$TMP/restored.txt" || true; fi
awk '$2>0' "$TMP/restored.txt" | head -40
