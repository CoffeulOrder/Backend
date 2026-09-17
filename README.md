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
| Spring Boot | 3.5.3 | ⚠️ 설계안 ver 0.7엔 "4.1.x"로 적혀 있으나 Maven Central에 존재하지 않아 실제 최신 안정판으로 대체. 설계안 쪽 수정 필요 |
| Spring Modulith | 1.4.1 | ⚠️ 설계안엔 "2.1.1"로 적혀 있으나 동일 사유로 대체 |
| MySQL | 8.4 LTS | 운영 대상. `ddl-auto=validate`, 스키마 변경은 Flyway로만 |
| 빌드 | Maven | 설계안 확정 사항 |

## 로컬 실행

1. `.env.example`을 참고해 로컬에 `.env` 또는 환경변수로 값 채우기 (그대로 `.env`를 커밋하지 말 것 — `.gitignore` 처리됨)
2. MySQL 8.4(로컬 또는 Docker)에 `coffeul` 데이터베이스 생성
3. `./mvnw spring-boot:run`

## 모듈 구조

`com.coffeul.{module}` — `auth · member · verification · store · menu · order · payment · notification · settlement · file`.
다른 모듈은 상대 모듈의 `api` 패키지만 호출한다 (`./mvnw test`가 `ModularityTests`로 경계를 검사한다).

## 현재 상태 (2026-09-17)

- 프로젝트 세팅 + 공통 응답/에러(`common.response.ApiResponse`, `common.error.*`) + Flyway V1(`schema.sql`, 51/51 검증됨) 완료.
- 이 환경엔 Docker가 없어서 Testcontainers 기반 통합 테스트는 아직 추가 안 함(`pom.xml`엔 의존성만 있음). Docker 있는 환경에서 이어서 작성 필요.
- 다음: 메뉴 CSV → 시드 SQL 변환 스크립트, 기준 구현 1벌(메뉴 조회 · 주문 생성) + 테스트 뼈대.
