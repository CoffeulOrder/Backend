#!/bin/bash
# 개발 서버에서 실행: 지정한 브랜치(기본 develop)를 받아 빌드하고 교체한다.
#   sudo /opt/ormo/Backend/deploy/deploy.sh [git-ref]
# 실패하면 직전 이미지로 되돌린다. 수동 복구는 rollback.sh.
set -euo pipefail

# 스크립트 전체를 함수로 읽어 둔 뒤 실행한다 — 배포 도중 git checkout이 이 파일을 바꿔도 안전하다.
main() {
  # 두 배포가 겹치지 않게 잠근다(자동 배포와 수동 배포가 동시에 도는 경우).
  exec 9>/var/lock/ormo-deploy.lock
  flock -w 900 9 || { echo "[deploy] 다른 배포가 15분 넘게 끝나지 않았다." >&2; exit 1; }
  REF="${1:-develop}"
  cd "$(dirname "$0")"
  REGION=ap-northeast-2
  # 없어도 되는 값: Parameter Store에 없으면 기본값을 쓴다.
  opt() { aws ssm get-parameter --region "$REGION" --with-decryption --name "/ormo/dev/$1" --query Parameter.Value --output text 2>/dev/null || echo "$2"; }
  # 포트원 키는 Secrets Manager의 ormo/portone(JSON)에 있다. 거기에 없으면 Parameter Store(/ormo/dev/*)를 본다.
  PORTONE_JSON=$(aws secretsmanager get-secret-value --region "$REGION" --secret-id ormo/portone --query SecretString --output text 2>/dev/null || echo '{}')
  portone() { v=$(printf '%s' "$PORTONE_JSON" | python3 -c 'import sys,json; print(json.load(sys.stdin).get(sys.argv[1],""))' "$1" 2>/dev/null || true); [ -n "$v" ] && echo "$v" || opt "$1" ""; }
  param() { aws ssm get-parameter --region "$REGION" --with-decryption --name "/ormo/dev/$1" --query Parameter.Value --output text; }

  git fetch --quiet origin
  TARGET=$(git rev-parse --verify --quiet "origin/$REF^{commit}" || git rev-parse --verify --quiet "$REF^{commit}") \
    || { echo "[deploy] 브랜치나 커밋을 찾을 수 없다: $REF" >&2; exit 1; }
  # deploy/가 레포에 들어오기 전의 옛 커밋으로 가면 이 폴더의 파일이 사라진다. 옛 버전은 rollback.sh로 되돌린다.
  if git ls-files --error-unmatch docker-compose.yml >/dev/null 2>&1 && ! git cat-file -e "$TARGET:deploy/docker-compose.yml" 2>/dev/null; then
    echo "[deploy] $REF 에는 deploy/ 가 없다. 옛 버전은 ./rollback.sh <태그> 로 되돌려라." >&2; exit 1
  fi
  git checkout --quiet --force --detach "$TARGET"
  SHA=$(git rev-parse --short HEAD)
  PREV=$(cat .current-tag 2>/dev/null || echo "")

  umask 077
  cat > .env <<ENV
IMAGE_TAG=$SHA
DB_PASSWORD=$(param DB_PASSWORD)
DB_ROOT_PASSWORD=$(param DB_ROOT_PASSWORD)
JWT_SECRET=$(param JWT_SECRET)
MAIL_PROVIDER=$(opt MAIL_PROVIDER log)
PAYMENT_GATEWAY=$(opt PAYMENT_GATEWAY fake)
PORTONE_API_SECRET=$(portone PORTONE_API_SECRET)
PORTONE_WEBHOOK_SECRET=$(portone PORTONE_WEBHOOK_SECRET)
PORTONE_WEBHOOK_ENABLED=$(opt PORTONE_WEBHOOK_ENABLED false)
ENV

  echo "[deploy] $REF ($SHA) 빌드"
  docker compose build backend
  docker compose up -d

  echo "[deploy] 상태 확인"
  for i in $(seq 1 60); do
    if docker compose exec -T caddy wget -qO- http://backend:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
      echo "$SHA" > .current-tag
      [ -n "$PREV" ] && [ "$PREV" != "$SHA" ] && echo "$PREV" > .previous-tag
      echo "[deploy] 성공: $SHA"
      docker image prune -f >/dev/null
      exit 0
    fi
    sleep 3
  done

  echo "[deploy] 실패: 서버가 3분 안에 안 떴다." >&2
  docker compose logs --tail 80 backend >&2 || true
  if [ -n "$PREV" ]; then
    echo "[deploy] 직전 이미지($PREV)로 되돌린다." >&2
    sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=$PREV/" .env
    docker compose up -d --no-build
  fi
  exit 1
}
main "$@"
