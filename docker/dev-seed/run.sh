#!/bin/sh
# docker-compose.yml의 dev-seed 서비스가 실행한다. Flyway가 끝나기를 기다렸다가 가짜 데이터를 넣는다.
# 이미 넣었으면(가짜 사업자번호 행이 있으면) 아무것도 안 하고 끝난다. 꼬였으면 `docker compose down -v` 후 다시 up.
set -eu

MYSQL="mysql -h mysql -u coffeul --default-character-set=utf8mb4 coffeul"   # 비밀번호는 MYSQL_PWD 환경변수
query() { $MYSQL -N -B -e "$1"; }

echo "[dev-seed] Flyway 마이그레이션 완료 대기 중..."
tries=0
until [ "$(query "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1" 2>/dev/null || echo 0)" -ge 2 ]; do
  tries=$((tries + 1))
  if [ "$tries" -gt 90 ]; then
    echo "[dev-seed] 3분 안에 Flyway가 안 끝났다. backend 로그를 확인해라: docker compose logs backend" >&2
    exit 1
  fi
  sleep 2
done

if [ "$(query "SELECT COUNT(*) FROM merchant WHERE business_reg_no = '9999999999'")" -gt 0 ]; then
  echo "[dev-seed] 이미 들어 있다. 건너뜀."
  exit 0
fi

echo "[dev-seed] 가짜 사장님·매장·계정 입력"
$MYSQL < /dev-seed/dev-seed.sql

for storeName in 범석관점 뉴밀레니엄관점; do
  storeId=$(query "SELECT id FROM store WHERE name = '$storeName'")
  echo "[dev-seed] 메뉴 시드 입력: $storeName (store_id=$storeId)"
  { echo "SET @coffeul_store_id = $storeId;"; echo "SOURCE /menu-seed/menu-seed.sql;"; } | $MYSQL
done

echo "[dev-seed] 완료"
