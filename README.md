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
| springdoc-openapi | 2.8.6 | 2026-09-19 추가. Maven Central 실제 최신 안정판 확인 후 반영(2025-03 배포) |

## 로컬 실행

1. `.env.example`을 참고해 로컬에 `.env` 또는 환경변수로 값 채우기 (그대로 `.env`를 커밋하지 말 것 — `.gitignore` 처리됨)
2. MySQL 8.4(로컬 또는 Docker)에 `coffeul` 데이터베이스 생성
3. `./mvnw spring-boot:run`
4. API 문서: `http://localhost:8080/swagger-ui/index.html` (원본 JSON은 `/v3/api-docs`). 설정은 `common.config.OpenApiConfig`(제목 · 설명만, 나머지는 springdoc 기본값) — `common`이 `@ApplicationModule(OPEN)`이라 이 자리에 둠.

## 서버 한 번에 띄우기 (프런트용, Docker Compose)

프런트 팀원이 Java·MySQL 설치 없이 서버를 로컬에 띄워보는 방법. Docker(Docker Desktop 또는 Colima)만 있으면 된다.

```bash
docker compose up --build -d      # 첫 빌드는 Maven 의존성 때문에 몇 분 걸린다
curl localhost:8080/actuator/health
docker compose down               # 끄기 (데이터는 남음). 데이터까지 지우고 처음부터: docker compose down -v
```

- 뜨는 것: MySQL 8.4(호스트 포트 **3307**), 서버(**8080**, Swagger는 `/swagger-ui/index.html`), 그리고 가짜 데이터를 넣고 끝나는 `dev-seed`.
- 결제는 가짜 게이트웨이(`PAYMENT_GATEWAY=fake`), 인증 메일은 실제로 안 보내고 **서버 로그에 코드를 찍는다**(`docker compose logs backend | grep 가짜`) — 회원가입 흐름을 테스트할 땐 거기서 코드를 확인한다.
- 시드에는 진짜 사업자 정보가 없다. 상호·주소는 `[DEV]` 표시가 붙은 가짜이고, 계정 비밀번호는 전부 `dev-password-1234`다.

| 종류 | 아이디 | 로그인 API | 접근 |
|---|---|---|---|
| 고객 | `dev-member@g.eulji.ac.kr` | `POST /api/v1/auth/login` (`email`, `password`) | 학교 = 을지대 |
| 사장님 | `dev-owner` | `POST /api/v1/auth/staff/login` (`loginId`, `password`) | 범석관점 · 뉴밀레니엄관점 |
| 직원 | `dev-staff` | `POST /api/v1/auth/staff/login` | 범석관점만 |

- 메뉴는 두 매장 모두 공식 메뉴 41개(`db/seed/menu-seed.sql`)가 들어 있다. `docker/dev-seed/dev-seed.sql`은 **로컬 전용**이고 운영 DB에 넣지 않는다 — Flyway 마이그레이션이 아니다.
- 서버 코드를 바꾸면 `docker compose up --build -d`로 다시 빌드한다.

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
  - 토큰을 컨트롤러에서 실제로 받는 배선(`AuthUser` 인자 리졸버)은 아래 "기준 구현 (AuthUser 인자 리졸버)" 절에 있다. `JWT_SECRET` 로컬 기본값은 개발 전용이라 운영에선 반드시 Secrets Manager 값으로 덮어써야 함.

## 기준 구현 (이메일 인증)

- **SM-1 · SM-2** (`POST /verifications/email`, `/verifications/email/confirm`) — verification 모듈, REQ-EV-001~007 그대로.
  - 6자리 코드는 5분, 확인 성공 시 받는 인증 토큰(`evt_...`)은 30분. **둘 다 DB엔 SHA-256 해시만 남는다.**
  - 재발송 제한 60초(EV002) · 하루 10회(EV003, Asia/Seoul 자정 기준) · 확인 5회 실패 시 코드 폐기(EV005).
  - 메일은 `VerificationMailSender` 포트 뒤에 있고 **어댑터가 둘**이다 — `log`(기본)와 `ses`. `MAIL_PROVIDER`로 고른다.
    로컬에서 인증코드는 서버 로그의 `[가짜 메일]` 줄에서 본다.
  - **SES 어댑터는 있지만 켜지 마라.** 샌드박스 상태에서는 검증된 주소로만 발송돼서, 해제 전에 `MAIL_PROVIDER=ses`로
    올리면 실제 학생 메일이 전부 EV007로 막히고 **가입 자체가 막힌다.** 발신 도메인 SPF·DKIM + 샌드박스 해제가
    끝난 뒤에 전환한다. 자격 증명은 기본 제공자 체인(EC2 IAM 역할)에 맡기고 액세스 키를 설정에 두지 않는다.
    본문은 HTML 없이 텍스트로만 보낸다 — 코드 한 줄이라 HTML이 얻는 게 없고, 학교 메일함(Google Workspace)에서
    스팸으로 가면 서비스가 통째로 막힌다.
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
  - 응답의 `school.name`·`campus`는 `SchoolLookupPort` 뒤에서 채운다. **2026-09-19에 예고대로 교체 완료** —
    민섭이 `store.api.SchoolQueryApi`를 열면서, JdbcTemplate로 직접 읽던 `TemporarySchoolLookupAdapter`를
    `StoreSchoolLookupAdapter`(store.api 위임)로 바꿨다. 포트·서비스·응답은 한 줄도 안 바뀌었다 —
    임시 어댑터를 일부러 JPA 엔티티로 매핑하지 않았던 게 여기서 값을 했다.

- **SM-6** (`POST /members/password-reset`) — REQ-U-006. PASSWORD_RESET 인증 토큰으로 새 비밀번호 설정.
  - 성공하면 그 회원의 **refresh 토큰을 전부 폐기**하고 **로그인 잠금도 푼다**.
    폐기는 정상 회전이 아니라 강제 폐기라 만료 시각도 같이 당긴다 — 재사용 감지의 30초 유예 창으로 되살아나지 않게.
    잠금을 안 풀면 5회 틀려서 잠긴 사람이 재설정하고도 15분을 더 기다려야 해서 재설정한 의미가 없다.
  - 이를 위해 `auth.api.RefreshTokenRevoker`를 새로 열었다 (refresh_token을 다루는 코드는 auth 뒤에만 둔다).
  - 비밀번호 규칙 검사를 토큰 소비보다 **먼저** 한다 — 오타 하나로 인증 토큰이 타버리면 메일부터 다시 받아야 한다.

- **SM-4** (`GET /members/me`) — REQ-U-004. 이름 · 이메일 · 학교 · 가입일.
  - `createdAt`은 DB에 UTC로 들어 있고 명세 예시는 `+09:00`이라 **영업 시간대(`coffeul.business-zone`)로 변환**해서 내보낸다. 서버 시간대에 흔들리지 않게 하려는 것.
  - 학교 행을 못 찾아도 500으로 터뜨리지 않고 이름·캠퍼스를 빈 값으로 내려보낸다 — 내 정보 화면이 학교 때문에 통째로 실패하면 안 된다.

- **SM-5** (`PATCH /members/me/password`) — REQ-U-005. 현재 비밀번호 확인 후 변경.
  - 성공하면 **refresh 토큰을 전부 폐기**한다. 비밀번호를 바꾸는 이유가 "남이 내 계정을 쓰고 있다"인 경우가 많아서, 기존 세션을 두면 침입자가 그대로 로그인 상태로 남는다. 성공 메시지가 "다시 로그인해주세요"인 이유다.
  - 검사 순서는 현재 비밀번호(U004) → 새 비밀번호 규칙(U002). SM-6과 달리 **소비되는 인증 토큰이 없어서** 순서를 뒤집을 이유가 없다.

- **SM-7** (`DELETE /members/me`) — REQ-U-007. 비밀번호 확인 후 탈퇴.
  - 진행 중 주문이 있으면 **U005 + `data.activeOrderCount`**로 막는다. 돈이 걸린 주문을 두고 계정을 비우면 환불 · 픽업 연락이 갈 곳이 없어진다.
  - 개인정보만 지우고 **행은 남긴다**: `email`·`password_hash` → NULL, `name` → '탈퇴회원', `status` → WITHDRAWN, `withdrawn_at` 기록. 주문 · 결제가 `member_id`로 이 행을 가리키고 전자상거래법 시행령 제6조가 5년 보존을 요구해서, 행을 지우면 그 기록이 끊긴다.
  - `email`을 NULL로 두므로 `uk_member_email`에 걸리지 않아 **같은 이메일로 재가입이 된다** (통합 테스트로 확인).
  - 개인정보 삭제 · refresh 폐기 · 푸시 토큰 비활성이 **한 트랜잭션**이다 (명세: "한 번에"). 계정은 사라졌는데 푸시는 계속 가는 중간 상태를 만들지 않는다.

- **세 API 공통 — 주체 종류를 확인한다.** 권한이 CUSTOMER라서 컨트롤러가 `AuthUser.type`이 MEMBER인지 먼저 본다(아니면 **C003**). 직원 토큰의 `sub`는 `staff_account.id`인데 그대로 쓰면 같은 숫자의 `member.id`를 남의 정보로 읽거나 **탈퇴시켜 버린다.** 리졸버는 토큰을 복원만 하고 권한은 보지 않으므로 이 확인은 각 API의 몫이다.
- **세 API 공통 — 탈퇴 · 정지 계정은 C002.** access 토큰은 폐기해도 최대 1시간 살아 있다(REQ-AUTH-012). 명세가 이 API들의 인증 실패를 C002 하나로 정의해 둬서 같은 코드로 막는다.

- **`member` ↔ `order` 순환 의존을 역방향 포트로 끊었다.** SM-7은 진행 중 주문 수가 필요한데, `order`가 이미 주문 생성(MS-16)에서 `member.api.MemberStatusApi`를 부르고 있어서 member가 `order.api`를 직접 부르면 **Modulith가 빌드를 깬다**(실제로 깨졌다). 그래서 `member.api.ActiveOrderCountPort`를 member가 정의하고 `order.infrastructure.ActiveOrderCountAdapter`가 구현한다 — verification이 `MemberAccountPort`를 정의하고 member가 구현하는 것과 같은 방식이다. "진행 중"의 정의는 주문 모듈 지식이라 어댑터가 민섭의 `OrderQueryApi#countActiveOrders`에 그대로 위임한다(종료 상태 목록을 두 군데 두면 탈퇴 판정만 조용히 어긋난다).
- **푸시 토큰 비활성은 임시 어댑터다.** `device_token`의 주인 모듈(notification, 태완 형)이 아직 없어서 `PushTokenDeactivationPort` 뒤에 JdbcTemplate 어댑터를 뒀다. 모듈이 생기면 **어댑터만** notification.api 호출로 바꾼다 — 학교 조회가 걸어간 길과 같다.

## 기준 구현 (AuthUser 인자 리졸버)

`auth.infrastructure.security.AuthUserArgumentResolver` — 컨트롤러가 인자에 `auth.api.AuthUser`를 적기만 하면 Authorization 헤더의 access 토큰에서 채워진다. 원래 민섭 담당이었는데 MS-22~26이 MS-27(결제 승인) 선행 때문에 밀려서 2026-09-19에 성민이 맡았다.

- **애노테이션을 만들지 않았다.** `@LoginUser` 같은 걸 두지 않고 타입으로만 판별한다 — 스펙이 "컨트롤러는 `AuthUser`만 받는다"고 정했으므로 다른 모듈이 추가로 알아야 할 것을 늘리지 않는다. 커스텀 리졸버는 애노테이션 없는 객체를 전부 받아버리는 ModelAttribute 리졸버보다 앞에서 호출되므로 요청 파라미터 바인딩으로 흘러가지 않는다.
- 파싱은 이 클래스 하나에만 있다 (스펙: "토큰 파싱 코드를 각 모듈에 두지 않는다"). 실제 검증·복원은 기존 `JwtAccessTokenService#decode`를 그대로 쓴다 — 새로 만든 건 헤더에서 토큰을 꺼내 MVC에 꽂는 부분뿐이다.
- 헤더 없음 · `Bearer` 아닌 스킴 · 토큰 자리 공백 · 서명 불일치 · 만료 → 전부 **C002(401)**. 스킴은 RFC 6750대로 대소문자를 구분하지 않는다(`bearer`도 받는다).
- **계정 상태(탈퇴 · 정지)는 여기서 확인하지 않는다.** REQ-AUTH-012는 주문 생성 · 결제 승인 · 매장용 API가 각자 DB에서 확인하도록 정했고, auth가 그걸 하려면 다른 모듈을 import해야 해서 모듈 경계(REQ-AUTH-011)가 깨진다.
- **MS-27과 함께 들어올 `JwtFilter` · `SecurityConfig`와 충돌하지 않는다.** 지금은 Spring Security 스타터가 없어서(pom에 `spring-security-crypto`만 있다) 리졸버가 직접 헤더를 읽는다. 필터가 앞단에서 검증하게 되면 리졸버 구현만 "필터가 넣어둔 값을 꺼내기"로 바꾸면 되고, 컨트롤러 시그니처는 그대로다.
- `AuthController#logout`은 아직 헤더를 직접 파싱한다 — auth 모듈 주인(민섭)의 파일이라 건드리지 않았다. 인자를 `AuthUser`로 바꾸고 `bearerToken` 메서드를 지우면 되는 한 줄짜리 정리다.
- 검증: `AuthUserArgumentResolverTest`(단위 9건 — 클레임 복원, 스킴 대소문자, 401 다섯 경로) + `AuthUserResolverWiringTest`(실제 컨텍스트에서 MVC 등록 여부 + **순서** 2건).
- **테스트 전용 컨트롤러를 띄우지 않았다** (다음 사람이 같은 함정에 빠지지 않도록 남김). 테스트 소스도 `com.coffeul` 아래라 `@RestController`를 붙이면 컴포넌트 스캔에 걸려서, `@Bean` 등록과 겹치면 "Ambiguous mapping"으로 컨텍스트가 죽고 스캔만 두면 그 엔드포인트가 **모든** 통합 테스트 컨텍스트와 springdoc 문서에 딸려 붙는다. `@RequestMapping`만 붙이는 우회는 핸들러로 등록되지 않아 404(정적 리소스)로 빠진다. 그래서 엔드포인트 대신 `RequestMappingHandlerAdapter`의 리졸버 목록을 직접 검사한다 — 공유 컨텍스트를 그대로 써서 빠르기도 하다.

## 기준 구현 (조회 포트 — order.api · store.api)

성민 형의 SM-1~6(이메일 인증 · 회원가입 · 비번 재설정) 작업이 필요로 하는 두 조회 포트. 배선(컨트롤러)은 없고 다른 모듈이 부르는 포트만 존재한다.

- **`order.api.OrderQueryApi#countActiveOrders(memberId)`** — SM-7(탈퇴) U005 판정용("진행 중인 주문이 있어 탈퇴할 수 없어요", `data.activeOrderCount`). 종료 상태(`COMPLETED`·`CANCELED`·`REJECTED`·`EXPIRED`) 4개를 제외한 나머지를 센다. MS-16의 `X-Member-Id` 헤더 stub과는 무관 — `Order`/`OrderRepository`가 이미 있어서 상태 전이(MS-22~26) 완성 여부와 별개로 바로 동작한다. 단 지금은 코드가 `PENDING_PAYMENT`만 실제로 쓰기 때문에, MS-22~26 전까진 "활성 주문 수"가 사실상 "전체 주문 수"와 같다.
- **`store.api.SchoolQueryApi#findById(schoolId)`** → `SchoolView(id, name, campus, status)`. SM-3·SM-4 응답이 요구하는 `school.name`·`campus`용. `school` 테이블은 V1에 이미 있고(성민 형이 만들 `school-seed` 마이그레이션은 실제 을지대 데이터를 채우는 것), 학교를 별도 모듈로 빼지 않고 store 모듈에 편입했다.
- 둘 다 `OrderQueryApiTest`·`SchoolQueryApiTest`(실제 MySQL 통합 테스트)로 검증.

## API 문서 (springdoc)

`springdoc-openapi-starter-webmvc-ui`로 컨트롤러에서 자동으로 OpenAPI 문서를 생성한다. 추가 애노테이션 없이 `@RestController`·`@RequestMapping` 그대로 반영됨 — 완료 기준의 "Swagger 응답 예시"는 이제 `/swagger-ui/index.html`에서 실제로 확인 가능하다. `OpenApiIntegrationTest`로 `/v3/api-docs`가 실제로 뜨고 컨트롤러 경로(`/api/v1/orders`)가 잡히는지 검증.

## 현재 상태 (2026-09-19)

- 프로젝트 세팅 + 공통 응답/에러 + Flyway V1(`schema.sql`, 51/51 검증됨) + 메뉴 시드 SQL + 기준 구현(메뉴 조회 · 주문 생성 · 인증) 완료.
- Testcontainers로 실제 MySQL 8.4에 붙는 통합 테스트 추가: 주문 생성 → 주문·주문항목·옵션이 정확한 금액으로 저장되는지, 같은 Idempotency-Key 재요청이 중복 주문을 만들지 않는지, 품절 메뉴 주문이 아예 저장되지 않는지 확인. 메뉴 시드 SQL도 실제 컨테이너에서 돌려서 41개 메뉴·7개 카테고리·105개 옵션이 정확히 들어가는지 확인함. 이어서 로그인·토큰 재발급·로그아웃 흐름도 통합 테스트로 검증.
  - 이 과정에서 실제 MySQL로만 잡을 수 있던 버그를 여럿 고쳤다(H2·모킹으로는 안 잡힘):
    - JPA 엔티티 필드 타입이 DB 물리 타입과 다름 — `OptionGroup.minSelect/maxSelect`(TINYINT인데 int), `OrderLine.quantity`(SMALLINT인데 int), `Order.requestHash`(CHAR(64)인데 columnDefinition 없는 String → VARCHAR로 추론).
    - **로그인 실패 카운트·재사용 감지 시 계열 폐기가 트랜잭션 롤백에 같이 사라지는 버그** — `BusinessException`을 던지기 전에 DB에 기록한 내용이, 그 예외 때문에 트랜잭션 전체가 롤백되면서 함께 사라짐(Spring 기본은 RuntimeException에서 전체 롤백). `noRollbackFor = BusinessException.class`로 고침 — 안 고쳤으면 5회 실패 잠금과 토큰 탈취 감지가 둘 다 조용히 작동 안 했을 것.
- **2026-09-19**: 성민 형의 SM-1~6 배선 요청 중 세 가지를 민섭이 맡아 완료 — `order.api.OrderQueryApi`(회원의 활성 주문 수, SM-7용), `store.api.SchoolQueryApi`(학교명 · 캠퍼스, SM-3 · SM-4용), springdoc(API 문서). 담당 조율 전체 내용은 옵시디언 `TEAM-Coffeul-스코프분배-설계.md` 2026-09-19 항목 참고. AuthUser 인자 리졸버는 성민 형 담당으로 넘어감.
- `./mvnw test` 통과: ModularityTests(모듈 경계 검증) 포함 **97개 전부 그린** (2026-09-19, AuthUser 리졸버 + SM-4 · 5 · 7까지 실측).
- 로컬 실행 조건: Java 21, Docker(Colima 포함) — 자세한 건 위 "통합 테스트" 절 참고.
- 다음: 주문 상태 전이(MS-22~26) — 단, MS-27(결제 승인)이 먼저 있어야 주문이 REQUESTED로 올라가고, MS-16도 결제 시도를 실제로 저장하도록 보강해야 함. 거절·취소는 환불 정책(Q2) 확정 전까지 보류. `school`·`merchant`·`store` 실데이터는 사장님 서류(Q3) 나오면. AuthUser 인자 리졸버는 성민 형 브랜치 push 대기.
