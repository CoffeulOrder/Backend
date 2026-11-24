# 개발 서버 운영 안내 (api.orso.it.kr)

EC2 한 대(`ORMO api`, t3.small, 서울)에 Caddy(HTTPS) · backend · MySQL 8.4를 Docker Compose로 올린다.
결제는 기본 fake 게이트웨이(`/ormo/dev/PAYMENT_GATEWAY`로 바꾼다), 데이터는 dev-seed의 가짜 값이다. 운영 서버가 아니다.

| 항목 | 값 |
|---|---|
| 주소 | https://api.orso.it.kr (문서: `/swagger-ui/index.html`, 상태: `/actuator/health`) |
| 서버 경로 | `/opt/ormo/Backend` (이 레포), 구성은 `deploy/` |
| 비밀값 | AWS Parameter Store `/ormo/dev/*`와 Secrets Manager `ormo/portone` — `deploy.sh`가 읽어 `deploy/.env`를 만든다. 커밋 금지 |
| 방화벽 | 80 · 443만 열려 있다. SSH는 닫혀 있다 |
| 백업 | S3 `ormo-db-backup-<계정ID>/dev/`, 매일 04:30 KST, 30일 보관 |

## 서버 접속

SSH 키가 없다. AWS Systems Manager로 들어간다(IAM 권한 필요).

```bash
aws ssm start-session --profile orso --target <인스턴스 ID>
sudo -i && cd /opt/ormo/Backend/deploy
```

## 배포

**자동**: `develop`에 머지되면 GitHub Actions(`.github/workflows/ci.yml`)가 테스트를 돌리고, 통과하면 `deploy` 작업이 이 서버에서 `deploy.sh <커밋>`을 실행한다. GitHub에는 AWS 키가 없다 — OIDC로 `ormo-github-deploy` 역할을 빌려 SSM 명령만 보낸다. 실패하면 Actions 실행이 빨갛게 끝나고 서버는 직전 이미지로 돌아가 있다.

**수동**(서버에서):

```bash
./deploy.sh            # origin/develop
./deploy.sh <브랜치 또는 커밋>
```

빌드 → 교체 → `/actuator/health`가 3분 안에 UP이 아니면 직전 이미지로 자동 복귀하고 실패로 끝난다.

## 복구 절차

**1. 새 버전이 문제일 때 (서버는 뜨는데 동작이 이상하다)**

```bash
./rollback.sh          # 직전에 성공한 이미지로
./rollback.sh <태그>   # docker images ormo-backend 에서 고른 태그로
```

DB 스키마는 되돌아가지 않는다(Flyway는 앞으로만 간다). 새 마이그레이션이 컬럼을 지웠다면 아래 3번까지 해야 한다.

**2. 컨테이너가 죽었을 때**

```bash
docker compose ps
docker compose logs --tail 200 backend
docker compose up -d   # 서버 재부팅 후에도 자동으로 뜬다(restart: unless-stopped)
```

**3. DB를 백업 시점으로 되돌릴 때** — 그 뒤에 쌓인 데이터는 사라진다.

```bash
aws s3 ls s3://ormo-db-backup-<계정ID>/dev/           # 되돌릴 파일 고르기
./backup.sh                                            # 지금 상태도 한 번 떠 둔다
docker compose stop backend
aws s3 cp s3://ormo-db-backup-<계정ID>/dev/<파일> /tmp/restore.sql.gz
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "DROP DATABASE coffeul"'
gunzip -c /tmp/restore.sql.gz | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot'
docker compose start backend
```

**4. 서버 자체를 잃었을 때** — 새 EC2(역할 `ormo-api-ec2`, 보안 그룹 `ormo-api`)에 Docker를 깔고 이 레포를 `/opt/ormo/Backend`에 받은 뒤 `deploy.sh` → 3번의 복구 → 고정 IP를 새 서버에 다시 연결한다.

## 백업 검증

```bash
./restore-verify.sh    # 최신 백업을 임시 MySQL에 복구해 테이블별 행 수를 원본과 비교. 운영 DB는 안 건드린다
```

2026-11-11 첫 검증: 31개 테이블 복구, 행 수 전부 일치.

## 가짜 데이터 다시 넣기

```bash
docker compose --profile seed run --rm dev-seed
```
