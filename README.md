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

## 현재 상태 (2026-09-17)

- 프로젝트 세팅 + 공통 응답/에러(`common.response.ApiResponse`, `common.error.*`) + Flyway V1(`schema.sql`, 51/51 검증됨) + 메뉴 시드 SQL(위) 완료.
- 이 환경엔 Docker가 없어서 Testcontainers 기반 통합 테스트는 아직 추가 안 함(`pom.xml`엔 의존성만 있음). Docker 있는 환경에서 이어서 작성 필요. 메뉴 시드 SQL도 실제 MySQL에 돌려본 적은 없음 — 카운트 검증(41개 메뉴 등)만 파이썬 단에서 통과.
- 다음: 기준 구현 1벌(메뉴 조회 · 주문 생성) + 테스트 뼈대.
