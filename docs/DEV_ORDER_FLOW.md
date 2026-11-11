# 개발용 주문·가짜 결제 흐름

2026-11-10. PG 심사·계약 정보를 기다리는 동안 기존 BE 코드를 이어서 검증하는 용도다.
실제 카드·간편결제·PortOne 호출은 없으며, 실결제 승인 API(MS-27)를 구현 완료한 것으로 세지 않는다.

## 활성화

`docker compose up --build -d`로 실행하면 로컬 Compose의 `dev` 프로필과 다음 설정이 적용된다.

```properties
coffeul.dev.payment-flow-enabled=true
coffeul.payment.gateway=fake
```

- 세 조건(dev 프로필·명시적 활성화·fake 게이트웨이)이 필요하다. `prod` 또는 `production`이 함께 활성화되면 개발용 컨트롤러와 서비스는 등록되지 않는다.
- 기본 실행은 활성화 값이 없으므로 기존 응답 계산용 가짜 어댑터를 유지한다.
- dev 흐름을 켜고 실제 게이트웨이 이름을 설정하면 가짜 성공을 반환하지 않고 PY006(503)으로 거절한다.
- 시드는 개발용 사업자번호 `9999999999`에 FAKE 설정만 추가한다. 기존 개발 DB도 `dev-seed`를 다시 실행하면 이 설정을 보충한다. 기존 거래/테이블을 지우지 않는다.
- 진짜 PG 정보·시크릿은 이 문서나 테스트 시드에 넣지 않는다.

## API 순서

| 순서 | API | 인증·입력 | 확인할 결과 |
| --- | --- | --- | --- |
| 1 | POST /api/v1/auth/login | 개발 고객 email/password | accessToken |
| 2 | GET /api/v1/stores | 고객 Bearer | storeId |
| 3 | GET /api/v1/stores/{storeId}/menus | 기존 공개 조회 | menuId·가격·hasOptions·품절 |
| 4 | POST /api/v1/orders | 고객 Bearer + Idempotency-Key | PENDING_PAYMENT, orderId, payment.pgOrderId·amount |
| 5 | POST /api/v1/dev/orders/{orderId}/confirm-payment | 고객 Bearer + {pgOrderId} | simulated=true, REQUESTED, pickupNo, payment.provider=FAKE |
| 6 | POST /api/v1/auth/staff/login | 개발 사장님 loginId/password | 사장님 accessToken |
| 7 | GET /api/v1/staff/stores/{storeId}/orders/active | 사장님 Bearer | 가짜 결제가 확정된 주문 카드 |
| 8 | POST /api/v1/staff/orders/{orderId}/accept | 사장님 Bearer | ACCEPTED |
| 9 | POST /api/v1/staff/orders/{orderId}/start | 사장님 Bearer | MAKING |
| 10 | POST /api/v1/staff/orders/{orderId}/ready | 사장님 Bearer | READY |
| 11 | POST /api/v1/staff/orders/{orderId}/complete | 사장님 Bearer | COMPLETED |
| 12 | GET /api/v1/orders/{orderId} | 고객 Bearer | 상태·픽업번호·타임라인 |

응답은 공통 `{code,message,data,...}` 봉투다. 주문 생성 시 같은 논리 요청의 재시도에는 같은 Idempotency-Key를 사용한다.

주문 생성 예시(숫자 ID는 실제 조회값/옵션 ID로 바꾼다):

```json
{
  "storeId": 1,
  "items": [{"menuId": 1, "quantity": 1, "optionItemIds": [2, 4]}],
  "withdrawalLimitAgreed": true
}
```

개발용 확정 본문:

```json
{"pgOrderId": "<주문 생성 응답의 payment.pgOrderId>"}
```

클라이언트가 승인 금액·회원ID·실제 PG 키를 보내는 API가 아니다. 서버가 저장한 금액·주문 주체·FAKE 시도를 확인한다.

## 저장·경합 보장

- 주문 생성과 FAKE `payment(READY)` 행 저장은 한 트랜잭션이다. PG 설정이 없으면 주문·항목·이력도 롤백한다.
- 주문 요청 재전송은 같은 시도 행을 재사용한다. 다른 고객·다른 결제 ID로 승인할 수 없다.
- 개발용 확정은 주문 행을 잠근 후 계정 상태·만료·영업 상태·메뉴/옵션 품절을 검사한다.
- `payment(APPROVED)`·`order(REQUESTED)`·KST 영업일별 픽업번호·`DEV_FAKE_PAYMENT` 상태 이력을 한 트랜잭션에서 기록한다.
- 같은 승인 동시 요청은 픽업번호·접수 이력을 한 번만 만든다. 같은 매장의 서로 다른 주문은 다른 픽업번호를 받는다.
- 이미 접수된 주문의 승인 재전송은 현재 상태를 돌려준다. 제조·픽업 진행 상태를 REQUESTED로 되돌리지 않는다.
- 픽업번호 발급 등이 실패하면 결제 승인도 롤백한다. 만료 작업은 남아 있는 FAKE READY 시도를 FAILED로 닫는다.

## 아직 남은 작업

- 실제 KG이니시스/PortOne 계약·테스트/실결제 채널·심사 결과 확인, SDK/WebView 방식·필드 계약 확정.
- 실제 PG 조회·웹훅 서명·멱등 확정·UNKNOWN 복구·결제 재시도·취소/거절/환불. 개발용 API를 그대로 실결제 API로 승격하지 않는다.
- MS-11 메뉴 상세·옵션 HTTP API가 없어, 옵션이 있는 메뉴의 옵션 ID를 앱이 조회하는 연결은 아직 남아 있다. 현재 통합 테스트는 테스트 데이터의 실제 ID로 요청한다.
- 실제 고객 앱·사장님 앱의 서버 연결과 기기 검증, CORS, 푸시 실제 발송.
- 기본 주문 상태는 기존 ACCEPTED/MAKING 분리를 유지한다. 닉네임·PIN·미수령·REFUNDED·관리자 웹 정책은 별도 합의 후 반영한다.

## 실행 검증

Java 21, Docker/Colima에서:

```sh
./mvnw --batch-mode --no-transfer-progress -Duser.timezone=UTC test
```

Colima 사용 시 JAVA_HOME(Java21), DOCKER_HOST, TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE를 README 방식으로 지정한다.
`DevOrderFlowIntegrationTest`는 실제 로그인·주문 생성·승인·사장님 처리·픽업 API를 호출하며, 주문 상태를 테스트 픽스처로 강제 승격하지 않는다.
`DevPaymentActivationTest`는 dev opt-in·prod 제외 조건을, `PersistentFakePaymentServiceTest`는 실제 게이트웨이 모드에서 가짜 승인이 막히는지 검증한다.

Git 상태는 로컬 개발 중이며 아직 커밋·push·PR·운영 배포를 하지 않았다. 기존 MS-16 인증·MS-17/18 변경도 같은 로컬 작업에 포함되어 있다.

최종 검증(2026-11-10): 전체 180건 통과, 실패·오류·스킵 0. MySQL 8.4/UTC JVM, 모듈 경계 검사 포함.
`docker compose config --quiet`, `sh -n docker/dev-seed/run.sh`, `git diff --check`도 통과했다.
실제 Compose 컨테이너를 새로 배포하거나 고객/사장님 앱 기기에서 실행한 결과는 아니다.
