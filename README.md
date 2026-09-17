# Coffeul Backend

학교 카페 원격 주문 서비스 백엔드. Spring Boot 모놀리식 + Spring Modulith로 모듈 경계를 코드/CI에서 강제한다.

## 문서

- 스코프 분배 (요구사항 명세 · 유스케이스 · 이벤트 스토밍 · ERD · 테이블 정의서 · REST API · 개발 규칙): https://claude.ai/artifact/1mNR2tesTpGbE6QuRoV6kB
- 백엔드 설계안 (아키텍처 결정): https://claude.ai/artifact/J9r8TfPAo7cvLG2iJSCbjV
- Notion (팀 공유, AI로 읽는 법 포함): 오르소 프로젝트 페이지

이 저장소의 코드가 위 문서와 어긋나면 **문서 원본(`docs/scope-src/*.py`)을 먼저 고치고 재발행한 뒤** 코드를 맞춘다.

## 스택

| 항목 | 값 | 비고 |
|---|---|---|
| Java | 21 LTS (Temurin) | |
| Spring Boot | 3.5.3 | 설계안 ver 0.7.1에서 정정됨(원래 "4.1.x" — Maven Central에 없어 실제 최신 안정판으로 교체) |
| Spring Modulith | 1.4.1 | 설계안 ver 0.7.1에서 정정됨(원래 "2.1.1") |
| MySQL | 8.4 LTS | 운영 대상. `ddl-auto=validate`, 스키마 변경은 Flyway로만 |
| 빌드 | Maven | 설계안 확정 사항 |

## 로컬 실행

1. `.env.example`을 참고해 로컬에 `.env` 또는 환경변수로 값 채우기 (그대로 `.env`를 커밋하지 말 것 — `.gitignore` 처리됨)
2. MySQL 8.4(로컬 또는 Docker)에 `coffeul` 데이터베이스 생성
3. `./mvnw spring-boot:run`

## 모듈 구조

`com.coffeul.{module}` — `auth · member · verification · store · menu · order · payment · notification · settlement · file`.
다른 모듈은 상대 모듈의 `api` 패키지만 호출한다 (`./mvnw test`가 `ModularityTests`로 경계를 검사한다).

## 메뉴 시드

`src/main/resources/db/seed/menu-seed.sql`은 공식 메뉴 CSV(102행)를 `docs/scope-src/menu_seed.py`(REQ-MN-006)로 변환한 것.
**Flyway 마이그레이션이 아니다** — `school`·`merchant`·`store`가 아직 없어서(사업자등록번호 등 진짜 값 없이는 만들지 않음), store가 생긴 뒤 매장마다 수동 실행한다.

```sql
SET @coffeul_store_id = 1; -- 범석관 store.id로 교체
SOURCE db/seed/menu-seed.sql;
-- 뉴밀레니엄관도 store_id만 바꿔서 한 번 더
```

메뉴 데이터를 고칠 땐 이 SQL을 직접 고치지 말고 `menu_seed.py`(또는 그 입력인 `menu-official-2026-09-10.json`)를 고친 뒤 재생성한다.

## 기준 구현 (메뉴 조회 · 주문 생성)

- **MS-9 · MS-10** (`GET /stores/{storeId}/categories`, `GET /stores/{storeId}/menus`) — menu 모듈, 완전히 동작.
- **MS-16** (`POST /orders`) — order 모듈. store.api(영업 상태) · menu.api(품절 · 옵션 · 가격) · member.api(계정 상태) · payment.api(결제창 정보)를 모듈 경계 그대로 호출해서 조립하는 구조를 보여주는 참조 구현.
  - **알려진 한계** (다음 사람이 이어받을 때 볼 것):
    1. auth 모듈이 없어 `X-Member-Id` 헤더로 회원을 임시 식별한다. 실제로는 `auth.api.AuthUser`로 교체.
    2. `payment` 테이블 행을 저장하지 않는다 — `merchant_pg`가 없어서(사업자 정보 미확정). 응답의 `payment` 블록은 `FakePaymentGatewayAdapter`가 계산만 해서 채운다.
    3. Idempotency-Key 재요청 시 결제 정보를 다시 계산해서 돌려준다(영속화가 없어서) — 가짜 어댑터가 orderCode만으로 결정되는 순수 함수라 지금은 우연히 일관되지만, 실제 PG 붙으면 재요청은 저장된 값을 그대로 반환하도록 바꿔야 한다.
  - 옵션 검증(`MenuSelectionValidator`)·가격 계산(`LinePricing`)은 DB·Spring 없이 도는 순수 로직 — `./mvnw test`로 지금 바로 돌아간다.

## 현재 상태 (2026-09-17)

- 프로젝트 세팅 + 공통 응답/에러 + Flyway V1(`schema.sql`, 51/51 검증됨) + 메뉴 시드 SQL + 기준 구현(메뉴 조회 · 주문 생성) 완료.
- `./mvnw test` 통과: ModularityTests(모듈 경계 11개 전부 통과, order → store·menu·payment·member로 정확히 잡힘) + 도메인 단위 테스트 8개.
- 이 환경엔 Docker가 없어서 Testcontainers 기반 통합 테스트(실제 MySQL에 저장까지 확인)는 아직 못 씀. 메뉴 시드 SQL도, 주문 생성 API도 실제 MySQL에는 아직 못 돌려봄 — school·merchant·store 실데이터가 없어서이기도 함(위 "메뉴 시드" 참고).
- 다음: `school`·`merchant`·`store` 실데이터(사장님 서류 나오면) → 메뉴 시드 실행 → Docker로 Testcontainers 통합 테스트 → 주문 상태 전이(수락·거절·환불) 등 나머지 API.
