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

## 통합 테스트 (Testcontainers)

`./mvnw test`는 실제 MySQL 8.4 컨테이너를 띄워서 Flyway 마이그레이션 + JPA 스키마 검증 + API까지 엔드투엔드로 확인한다
(`src/test/java/com/coffeul/AbstractIntegrationTest.java` — 전체 테스트 실행에서 컨테이너 하나를 공유하는 싱글턴).

- Java는 **21**이어야 한다 (`java -version`으로 확인. macOS에서 Homebrew로 17과 21이 같이 깔려있으면 `JAVA_HOME`을 21로 명시: `export JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home`).
- **Colima**를 쓰는 경우(Docker Desktop이 아니라) 소켓 경로가 표준이 아니라서 아래 두 환경변수가 필요하다:
  ```bash
  export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
  export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
  ```
  (`DOCKER_HOST`는 맥 쪽에서 Colima VM에 접속하는 경로, `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`는 Ryuk 컨테이너가 VM *내부*에서 인식하는 소켓 경로 — 이게 없으면 Ryuk가 "operation not supported"로 뜨지 않는다.)
- Docker Desktop을 쓰면 위 두 변수는 필요 없다.

## 모듈 구조

`com.coffeul.{module}` — `auth · member · verification · store · menu · order · payment · notification · settlement · file`.
다른 모듈은 상대 모듈의 `api` 패키지만 호출한다 (`./mvnw test`가 `ModularityTests`로 경계를 검사한다).

## 메뉴 시드

`src/main/resources/db/seed/menu-seed.sql`은 공식 메뉴 CSV(102행)를 `docs/scope-src/menu_seed.py`(REQ-MN-006)로 변환한 것.
**Flyway 마이그레이션이 아니다** — `merchant`·`store`가 아직 없어서(사업자등록번호 등 진짜 값 없이는 만들지 않음), store가 생긴 뒤 매장마다 수동 실행한다.
(`school`과 `school_email_domain`은 사업자 정보가 필요 없어서 V2 마이그레이션으로 들어가 있다 — 아래 "학교 데이터" 참고.)

```sql
SET @coffeul_store_id = 1; -- 범석관 store.id로 교체
SOURCE db/seed/menu-seed.sql;
-- 뉴밀레니엄관도 store_id만 바꿔서 한 번 더
```

메뉴 데이터를 고칠 땐 이 SQL을 직접 고치지 말고 `menu_seed.py`(또는 그 입력인 `menu-official-2026-09-10.json`)를 고친 뒤 재생성한다.

## 학교 데이터

`V2__seed_eulji_school.sql` — 을지대학교 성남캠퍼스 + 이메일 도메인 `g.eulji.ac.kr`.

`school_email_domain`에 행이 없으면 REQ-EV-001이 **모든 가입을 EV001로 막아서** 서버가 회원을 하나도 못 받는다.
통합 테스트는 각자 도메인을 심어서 통과하므로, 진짜 도메인이 동작하는지는 `SchoolSeedIntegrationTest`에서만 확인된다.

새 학교를 받을 땐 코드가 아니라 같은 방식으로 행을 추가한다 (설계안: "학교는 코드가 아니라 데이터").
스키마 변경이 아니라 기준 데이터라서 `erd.py`는 건드리지 않는다.

## 기준 구현 (메뉴 조회 · 주문 생성)

- **MS-9 · MS-10** (`GET /stores/{storeId}/categories`, `GET /stores/{storeId}/menus`) — menu 모듈, 완전히 동작.
- **MS-16** (`POST /orders`) — order 모듈. store.api(영업 상태) · menu.api(품절 · 옵션 · 가격) · member.api(계정 상태) · payment.api(결제창 정보)를 모듈 경계 그대로 호출해서 조립하는 구조를 보여주는 참조 구현.
  - **알려진 한계** (다음 사람이 이어받을 때 볼 것):
    1. auth 모듈이 없어 `X-Member-Id` 헤더로 회원을 임시 식별한다. 실제로는 `auth.api.AuthUser`로 교체.
    2. `payment` 테이블 행을 저장하지 않는다 — `merchant_pg`가 없어서(사업자 정보 미확정). 응답의 `payment` 블록은 `FakePaymentGatewayAdapter`가 계산만 해서 채운다.
    3. Idempotency-Key 재요청 시 결제 정보를 다시 계산해서 돌려준다(영속화가 없어서) — 가짜 어댑터가 orderCode만으로 결정되는 순수 함수라 지금은 우연히 일관되지만, 실제 PG 붙으면 재요청은 저장된 값을 그대로 반환하도록 바꿔야 한다.
  - 옵션 검증(`MenuSelectionValidator`)·가격 계산(`LinePricing`)은 DB·Spring 없이 도는 순수 로직 — `./mvnw test`로 지금 바로 돌아간다.

## 기준 구현 (인증)

- **MS-1 · MS-2 · MS-3 · MS-4** (`POST /auth/login`, `/auth/staff/login`, `/auth/refresh`, `/auth/logout`) — auth 모듈, rules.py AUTH_SPEC 그대로.
  - access 토큰은 JWT(HS256, 1시간), refresh 토큰은 JWT가 아닌 256비트 무작위 문자열 — DB엔 SHA-256 해시만 저장(`refresh_token` 테이블).
  - `auth`는 `member`·`store`를 직접 import하지 않는다 — `MemberCredentialPort`·`StaffCredentialPort`를 auth가 정의하고 각 모듈이 역방향으로 구현.
  - refresh 토큰 회전 + 재사용 감지(탈취 의심 시 같은 계열 전부 폐기, 단 폐기 후 30초 안이면 동시 요청으로 보고 새 쌍만 발급), 로그인 실패 잠금(고객 5회→15분 / 직원 5회째부터 30초 대기)까지 구현하고 통합 테스트로 검증.
  - **알려진 한계**: 다른 컨트롤러(주문 등)에서 이 토큰을 실제로 검사하는 배선(`AuthUser` 인자 리졸버)은 아직 없음 — MS-22~26(주문 상태 전이) 만들 때 이어붙일 예정. `JWT_SECRET` 로컬 기본값은 개발 전용이라 운영에선 반드시 Secrets Manager 값으로 덮어써야 함.

## 기준 구현 (이메일 인증)

- **SM-1 · SM-2** (`POST /verifications/email`, `/verifications/email/confirm`) — verification 모듈, REQ-EV-001~007 그대로.
  - 6자리 코드는 5분, 확인 성공 시 받는 인증 토큰(`evt_...`)은 30분. **둘 다 DB엔 SHA-256 해시만 남는다.**
  - 재발송 제한 60초(EV002) · 하루 10회(EV003, Asia/Seoul 자정 기준) · 확인 5회 실패 시 코드 폐기(EV005).
  - 메일은 `VerificationMailSender` 포트 뒤에 있고 지금은 **로그 어댑터**만 있다(`MAIL_PROVIDER=log`).
    로컬에서 인증코드는 서버 로그의 `[가짜 메일]` 줄에서 본다. SES 어댑터는 발신 도메인 확보 + 샌드박스 해제 후.
  - `verification`은 `member`를 import하지 않는다 — 가입 여부 확인은 verification이 `MemberAccountPort`를 정의하고
    member가 역방향으로 구현한다(그러지 않으면 SM-3에서 순환 의존).
  - 인증코드 정리 스케줄(매일 04:00 KST, 만료 후 7일 지난 행 삭제)까지 포함.
  - **팀 결정 대기**: REQ-EV-006(이미 가입된 이메일에 EV006으로 알려줄지)은 명세가 '미정'이라 일단 알려주는 쪽으로 구현했다.
    숨기는 쪽으로 정해지면 `SendVerificationCodeService`의 SIGNUP 분기만 PASSWORD_RESET과 같은 방식으로 바꾸면 된다.

## 기준 구현 (회원가입)

- **SM-3** (`POST /members`) — member 모듈, REQ-U-001 · 002 · 003 + REQ-EV-005.
  - 이메일은 요청 본문으로 받지 않는다 — **인증 토큰에 묶인 이메일**을 쓴다(다른 이메일로 바꿔치기 방지).
  - 회원 · 약관 · 토큰 소비가 한 트랜잭션. 약관 미동의(U003)나 비밀번호 규칙 위반(U002)으로 막히면
    인증 토큰은 쓰이지 않은 상태로 남아서 그대로 다시 제출할 수 있다.
  - 비밀번호는 8자 이상 **72바이트 이하** + 영문·숫자 (REQ-U-002). 72바이트는 BCrypt가 그 뒤를 잘라내기 때문 —
    넘겨서 저장하면 사용자가 입력한 것과 실제 검사되는 것이 달라진다.
  - 토큰 발급은 `auth.api.TokenIssuer`로만 한다 — member는 JWT를 모른다.
  - **임시 처리**: 응답의 `school.name`·`campus`는 `SchoolLookupPort` 뒤의
    `TemporarySchoolLookupAdapter`(JdbcTemplate 읽기 한 줄)가 채운다.
    `school` 테이블의 주인 모듈이 아직 없어서인데(학교·사장님·매장 뼈대는 민섭 담당),
    정해지면 **이 어댑터만** store.api 호출로 바꾸면 되고 서비스·응답은 그대로다.
    일부러 JPA 엔티티로 매핑하지 않았다 — 매핑하면 member가 남의 테이블을 소유하는 모양이 된다.

- **SM-4 · SM-5 · SM-7은 아직 없다.** 셋 다 권한이 CUSTOMER인데 `auth.api.AuthUser`를 컨트롤러 인자로
  받을 리졸버가 아직 없어서다(민섭, MS-22~26과 함께 예정). SM-6은 권한이 없어 리졸버와 무관하지만
  "성공 시 refresh 토큰 전부 폐기"를 할 포트가 `auth.api`에 없다.

## 현재 상태 (2026-09-18)

- 프로젝트 세팅 + 공통 응답/에러 + Flyway V1(`schema.sql`, 51/51 검증됨) + 메뉴 시드 SQL + 기준 구현(메뉴 조회 · 주문 생성 · 인증) 완료.
- Testcontainers로 실제 MySQL 8.4에 붙는 통합 테스트 추가: 주문 생성 → 주문·주문항목·옵션이 정확한 금액으로 저장되는지, 같은 Idempotency-Key 재요청이 중복 주문을 만들지 않는지, 품절 메뉴 주문이 아예 저장되지 않는지 확인. 메뉴 시드 SQL도 실제 컨테이너에서 돌려서 41개 메뉴·7개 카테고리·105개 옵션이 정확히 들어가는지 확인함. 이어서 로그인·토큰 재발급·로그아웃 흐름도 통합 테스트로 검증.
  - 이 과정에서 실제 MySQL로만 잡을 수 있던 버그를 여럿 고쳤다(H2·모킹으로는 안 잡힘):
    - JPA 엔티티 필드 타입이 DB 물리 타입과 다름 — `OptionGroup.minSelect/maxSelect`(TINYINT인데 int), `OrderLine.quantity`(SMALLINT인데 int), `Order.requestHash`(CHAR(64)인데 columnDefinition 없는 String → VARCHAR로 추론).
    - **로그인 실패 카운트·재사용 감지 시 계열 폐기가 트랜잭션 롤백에 같이 사라지는 버그** — `BusinessException`을 던지기 전에 DB에 기록한 내용이, 그 예외 때문에 트랜잭션 전체가 롤백되면서 함께 사라짐(Spring 기본은 RuntimeException에서 전체 롤백). `noRollbackFor = BusinessException.class`로 고침 — 안 고쳤으면 5회 실패 잠금과 토큰 탈취 감지가 둘 다 조용히 작동 안 했을 것.
- `./mvnw test` 통과: ModularityTests(모듈 경계 12개 전부) + 도메인 단위 테스트 8개 + 통합 테스트 9개 = 19개 전부 그린.
- 로컬 실행 조건: Java 21, Docker(Colima 포함) — 자세한 건 위 "통합 테스트" 절 참고.
- 다음: 주문 상태 전이(MS-22~26) — 단, MS-27(결제 승인)이 먼저 있어야 주문이 REQUESTED로 올라가고, MS-16도 결제 시도를 실제로 저장하도록 보강해야 함. 거절·취소는 환불 정책(Q2) 확정 전까지 보류. `school`·`merchant`·`store` 실데이터는 사장님 서류(Q3) 나오면.
