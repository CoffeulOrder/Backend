# 개발용 주문·가짜 결제 흐름

2026-11-10. PG 심사·계약 정보를 기다리는 동안 기존 BE 코드를 이어서 검증하는 용도다.
이 절의 개발용 흐름은 카드·간편결제·PortOne을 호출하지 않는다. PortOne 연동 개발은 아래 MS-16·27 절에 정리한다.

## 활성화

`docker compose up --build -d`로 실행하면 로컬 Compose의 `dev` 프로필과 다음 설정이 적용된다.

```properties
coffeul.dev.payment-flow-enabled=true
coffeul.payment.gateway=fake
```

- 세 조건(dev 프로필·명시적 활성화·fake 게이트웨이)이 필요하다. `prod` 또는 `production`이 함께 활성화되면 개발용 컨트롤러와 서비스는 등록되지 않는다.
- 기본 실행은 활성화 값이 없으므로 기존 응답 계산용 가짜 어댑터를 유지한다.
- PortOne 게이트웨이를 선택하면 개발용 가짜 결제 확정 API는 등록되지 않는다.
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
- 테스트 채널에서 실제 PG 조회·확정과 서명된 웹훅 수신, 고객 취소·매장 거절·만료 주문의 실제 전액 환불, 같은 결제번호의 결제창 재개를 검증한다. 수락 후 매장 환불은 후속 작업이다.
- 고객 앱의 메뉴 상세·옵션 조회는 `GET /api/v1/stores/{storeId}/menus/{menuId}`와 연동해 기기에서 검증한다.
- 실제 고객 앱·사장님 앱의 서버 연결과 기기 검증, CORS, 푸시 실제 발송.
- 기본 주문 상태는 기존 ACCEPTED/MAKING 분리를 유지한다. 닉네임·PIN·미수령·REFUNDED·관리자 웹 정책은 별도 합의 후 반영한다.

## PortOne V2 KG이니시스 MS-16·27

개발 검증 환경에서 `coffeul.payment.gateway=portone`을 선택한다. `coffeul.payment.portone.api-secret`에는 해당 PortOne 계정의 V2 API Secret을 서버에만 주입한다. 현재 어댑터는 서버 인스턴스당 한 PortOne 계정의 Secret을 사용한다.

매장에 연결된 `merchant_pg`에 `pg_provider=INICIS_V2`, `status=ACTIVE`, `portone_store_id`(PortOne 상점 ID), `client_key`(결제창 채널 키)가 있어야 한다. `mid`는 KG이니시스 식별자다. V3는 상점 ID와 결제 시도에 보관할 상점·채널 컬럼을 추가한다.

1. `POST /api/v1/orders`에 고객 Bearer와 `Idempotency-Key`를 보낸다. `payment.portOne`의 `storeId`, `channelKey`, `paymentId`, `currency`와 `payment.orderName`, `payment.amount`로 결제창을 연다. 구매자 이름은 고객 앱에서 전달한다. 같은 주문 생성 요청은 저장된 첫 결제 시도의 정보를 반환한다.
2. 결제창 복귀 후 고객 Bearer로 `POST /api/v1/payments/confirm`에 `{ "orderId": 1, "paymentId": "<결제 식별자>" }`를 보낸다. `orderId`는 주문 생성 응답값이다. 서버는 PortOne에서 `PAID` 여부, 결제 ID·상점·채널·PG·통화와 저장된 주문 금액을 확인한다.
3. 결제 승인·주문 접수·픽업번호·이력을 한 트랜잭션으로 저장한다. 확인 요청을 다시 보내면 이미 접수된 주문의 현재 상태를 반환한다.

PortOne 조회 지연·오류는 `PY010`, 결제 미확인은 `PY009`, 결제 식별 정보 불일치는 `PY011`, 금액 불일치는 `PY001`로 응답한다. 이미 PortOne에서 결제된 주문도 로컬 주문이 만료됐거나 접수 불가하면 주문을 접수하지 않는다. 고객의 수락 전 취소·환불은 아래 MS-19를 따른다. 만료 주문의 결제는 아래 대사·복구 경로로 전액 환불을 요청한다. 실제 테스트 채널 검증은 운영 결제 오픈 전에 완료한다.

### PortOne V2 웹훅

`coffeul.payment.gateway=portone` 환경에서 `coffeul.payment.portone.webhook-enabled=true`로 수신 경로를 켜고, 콘솔에서 발급한 웹훅 시크릿을 `coffeul.payment.portone.webhook-secret`에 서버 설정으로 주입한다. 테스트 모드에서 PortOne이 접근할 수 있는 HTTPS 주소의 `POST /api/v1/payments/webhooks/portone`을 등록하고 V2의 `2024-04-25` 본문 형식을 사용한다. [PortOne 웹훅 연동](https://developers.portone.io/opi/ko/integration/webhook/readme-v2?v=v2)

- 요청 본문 원본과 `webhook-id`, `webhook-timestamp`, `webhook-signature`로 서명을 검사한다. 발송 시각이 서버 시각과 5분 넘게 차이 나거나 서명이 유효하지 않으면 `401`을 반환한다.
- `Transaction.Paid`가 오면 저장한 상점·결제 식별자를 대조하고 PortOne API에서 조회한다. 앱 확인 API와 같은 트랜잭션으로 주문을 접수한다. 중복 알림과 앱 동시 요청은 접수 이력·픽업번호를 한 번만 만든다.
- 처리 완료, 이미 처리한 결제, 등록되지 않은 결제 식별자, 그 외 이벤트는 `204`로 응답한다. PG 일시 오류는 `503`으로 응답하며 주문은 다음 전달에서 다시 확인할 수 있다. 만료 주문의 결제가 검증되면 환불 요청을 저장한 뒤 `204`로 응답한다.

## 고객 주문 취소와 전액 환불 — MS-19

`POST /api/v1/orders/{orderId}/cancel`에 고객 Bearer를 보낸다. 본문은 생략하거나
`{"reason":"메뉴를 잘못 골랐어요"}`로 취소 사유를 보낼 수 있다(최대 200자).

- 고객 본인의 `REQUESTED` 주문만 취소할 수 있다. 매장 수락(`ACCEPTED`) 이후에는 `409/OD006`으로 현재 상태를 반환한다.
- 주문 행을 잠근 뒤 `CANCELED`, 취소 이력, `payment_cancel(REQUESTED)`를 같은 트랜잭션에 저장한다. 환불 요청 저장이 실패하면 주문 취소도 롤백한다.
- 첫 응답은 HTTP 202이며 `data`에 `orderId`, `orderCode`, `status`, `canceledAt`, `refundStatus`, `refundAmount`를 담는다. 환불 요청 금액은 서버에 저장된 주문·승인 결제 금액을 사용한다.
- 같은 주문 취소를 재요청하면 기존 환불 상태를 반환한다. 주문 상세의 `refundStatus`로 처리 진행을 조회한다. `REQUESTED`는 환불 처리 중, `SUCCEEDED`는 PG 취소 확인 완료다. `FAILED`는 결제 정보나 금액 불일치 등으로 담당자 확인이 필요한 상태다. 카드사 계좌에 환불액이 입금되는 시점은 결제수단의 처리 일정에 따른다.
- 환불 작업은 요청을 DB에 저장하고, PG 호출은 주문 트랜잭션 밖에서 진행한다. 작업 도중 중단되거나 PG 응답을 놓쳐도 저장된 요청과 PG 결제 상태를 다시 확인한다.
- 개발용 FAKE 결제의 환불 시뮬레이션은 `dev` 프로필과 명시적 활성화가 필요하다. PortOne 환불은 `portone` 게이트웨이에서 처리한다.

FE는 취소 버튼의 활성화에 주문 상세의 `cancelable`을 사용하고, 취소 응답 뒤에는 주문 상세를 다시 조회한다.
수락과 취소가 경합할 때 서버는 주문 행 잠금으로 하나의 상태 변경만 허용한다.
웹훅 시크릿이나 API Secret을 요청 본문 또는 앱 설정에 전달하지 않는다.

[PortOne V2 취소 API](https://developers.portone.io/opi/ko/integration/cancel/v2/readme?v=v2)를 기준으로 테스트 응답을 검증한다.
KG이니시스 테스트 채널의 실제 환불 검증은 환경 전환 후 진행한다.

## 매장 주문 거절과 전액 환불 — MS-23

`POST /api/v1/staff/orders/{orderId}/reject`에 매장 OWNER 또는 배정된 STAFF의 Bearer와 아래 본문을 보낸다.

```json
{"reasonCode":"SOLD_OUT","reasonDetail":"오늘 재료가 소진됐어요"}
```

- 사유는 기존 DB의 `SOLD_OUT`, `INGREDIENT_SHORTAGE`, `STORE_BUSY`, `STORE_CLOSING`, `OTHER`를 사용한다. `OTHER`에는 상세 메모가 필요하며 메모는 최대 200자다.
- 활성 직원 계정, 현재 역할·사업자, 매장 접근 권한을 확인한다. `REQUESTED`에서만 거절할 수 있고 매장 수락 이후의 환불은 별도 작업이다.
- 거절 상태·사유·시각·이력과 전액 환불 요청을 같은 트랜잭션에 저장한다. 동일 사유의 재요청은 기존 환불 상태를 반환하며, 다른 사유로 이미 거절된 주문을 변경할 수 없다.
- 첫 응답은 HTTP 202다. `data`에는 `orderId`, `status`, `reasonCode`, `reasonDetail`, `rejectedAt`, `refundStatus`, `refundAmount`를 담는다. 고객 주문 상세의 `rejectReason`은 사유 코드이며 `rejectReasonDetail`과 `refundStatus`를 함께 조회할 수 있다.
- 매장 거절 환불은 `payment_cancel.requested_by_type=STAFF`로 저장한다. PortOne에는 환불 요청 주체를 `ADMIN`으로 전송한다. 고객 취소와 동일한 리스·재조회·재시도 경로를 사용한다.

## 만료 주문의 결제 대사·복구

주문이 `EXPIRED`이거나 `PENDING_PAYMENT`의 결제 기한이 지났는데 PG에서 결제가 확인되면 주문을 접수하지 않고 전액 환불을 요청한다.

- 앱의 `POST /api/v1/payments/confirm`은 환불 요청을 먼저 커밋한 뒤 `409/OD009`와 `data.orderId`, `status=EXPIRED`, `refundStatus`, `refundAmount`를 반환한다. 주문 상세에서도 환불 상태를 조회할 수 있다. 미결제·PG 조회 오류·정보 불일치는 기존 오류 코드를 따른다.
- `Transaction.Paid` 웹훅도 같은 복구 경로를 사용한다. 활성 회원 여부가 바뀌어도 만료된 결제의 환불은 막지 않는다. 앱 확인 호출에는 기존 고객 인증과 본인 주문 검증이 적용된다.
- 주문 만료와 결제 승인 기록, `payment_cancel(REQUESTED,SYSTEM)`은 한 트랜잭션이다. 이미 만료된 주문은 그대로 두며 픽업번호나 접수 이력을 만들지 않는다. 환불 저장이 실패하면 이번 트랜잭션의 만료·승인도 롤백한다.
- 별도 재조회 작업이 콜백 누락 건도 찾는다. V4의 `payment_reconciliation`에 건별 상태·리스·시도 번호·다음 조회 시각을 저장하므로 프로세스 재시작 후에도 이어서 처리한다. 외부 PG 조회는 DB 트랜잭션 밖에서 한다.
- 미결제(`PY009`)와 일시 오류는 재조회한다. 금액·상점·채널 불일치 등은 작업을 `FAILED`로 남겨 운영자 확인을 기다리며 자동 환불하지 않는다. 로그와 작업 테이블에는 고정 오류 코드만 남긴다.
- 환불 요청의 PG 실행은 기존 전액 환불 작업이 담당하며 요청 주체는 `ADMIN`이다. 앱 확인·웹훅·재조회가 겹쳐도 주문 잠금으로 환불 요청을 한 번만 저장한다.

`portone` 게이트웨이에서 재조회 작업은 기본 60초 뒤 시작하고 실행 간격도 60초다. 값이 없는 설정 키는 다음과 같다.

| 설정 | 기본값 | 역할 |
| --- | --- | --- |
| `coffeul.payment.recovery.enabled` | `true` | 주기 실행 활성화 |
| `coffeul.payment.recovery.batch-size` | `20` | 실행당 최대 처리 건수(1~100) |
| `coffeul.payment.recovery.lease-seconds` | `60` | 처리 중 다른 작업자의 조회를 막는 시간 |
| `coffeul.payment.recovery.retry-seconds` | `60` | 재조회 대기 기준. 시도 횟수에 따라 증가하며 최대 1시간 |
| `coffeul.payment.recovery.interval-ms` | `60000` | 주기 실행 간격 |
| `coffeul.payment.recovery.initial-delay-ms` | `60000` | 시작 후 첫 실행 대기 |

미결제·결제 미확인 건을 임의로 완료 처리하지 않고 재조회한다. 이 경로는 저장된 결제 시도를 조회하는 기능이며 아래 고객의 결제창 재개 요청과는 별도다. 실제 테스트 채널 환불은 아직 검증하지 않았다.

## 결제 상태 조회·결제창 재개 — MS-28·29

### 결제·환불 상태 조회

고객 Bearer로 `GET /api/v1/orders/{orderId}/payment`를 호출한다. 본인 주문과 활성 계정을 검사하며 실제 PG를 호출하거나 상태를 바꾸지 않는다. FAKE와 PortOne 모두 사용할 수 있다.

- `data`는 `orderId`, `orderStatus`, `expiresAt`, `pickupNo`, `retryAvailable`, `payment`, `refund`를 포함한다.
- `payment`에는 현재 결제의 `attemptNo`, `pgProvider`, `pgOrderId`, `status`, `amount`, `method`, `approvedAt`, `canceledAt`가 있다. 결제 준비 기록이 없으면 null이다.
- `refund`는 없으면 null이고, 있으면 `status`, `amount`, `succeededAt`를 포함한다. FAILED는 담당자 확인이 필요한 상태로 표시한다.
- `retryAvailable`은 서버에 저장된 상태 기준으로 재시도 버튼을 표시할지 판단하는 값이다. 실제 결제창을 다시 열기 전에는 아래 POST로 PG 상태를 확인해야 한다. UNKNOWN은 버튼을 비활성화하고 상태를 확인한다.
- 결제 거래 키·시크릿 참조·채널 키·PG 오류 원문은 조회 응답에 넣지 않는다.

### 기존 결제번호로 결제창 다시 열기

고객 Bearer로 `POST /api/v1/orders/{orderId}/payment-attempts`를 호출한다. 본문과 새 멱등 키는 필요 없다. 기존 명세의 PG 미확정 시점 결제번호 교체 제안은 현재 PortOne 구현에 그대로 적용하지 않는다. 저장된 동일 `paymentId`를 유지하며 새 payment 행을 만들지 않는다. `attemptNo`도 기존 행의 값이다.

응답은 HTTP 200이며 `data.action`에 따라 처리한다.

| action | 앱 처리 |
| --- | --- |
| `OPEN_CHECKOUT` | `data.payment`의 `pgOrderId`, `orderName`, `amount`, `portOne`으로 결제창을 다시 연다. 기존 결제번호·금액·상점·채널을 유지한다. |
| `VIEW_ORDER` | 결제창을 열지 않고 현재 주문 상세로 이동한다. 이미 접수된 주문 또는 PG 결제 확인 후 접수한 주문이다. `data.payment`은 null이다. |

- PG가 `READY`이거나 아직 결제 기록이 없고 로컬이 READY이면 같은 번호의 결제창을 다시 열 수 있다. PG가 FAILED이면 같은 번호를 유지해 재개한다.
- 로컬 UNKNOWN·FAILED는 PG의 FAILED가 검증돼야 READY로 복구한다. 단순 404·READY 조회만으로 결과 불명을 해제하지 않는다. PG의 진행 중·알 수 없는 상태는 `409/PY012`, 취소된 결제는 `409/PY008`로 결제창 재개를 막는다.
- PG PAID는 기존 결제 확인 경로로 검증·접수한 뒤 VIEW_ORDER를 반환한다. 조회 중 주문이 만료되면 기존 전액 환불 복구 경로를 사용하고 `409/OD009`와 환불 상태를 반환한다.
- PG 조회 오류는 `503/PY010`, 금액 불일치는 `400/PY001`, 식별 정보 불일치는 `409/PY011`이다. 실패 응답만으로 앱이 결제창을 열면 안 된다.
- PG 조회는 DB 트랜잭션 밖에서 수행한다. 조회 후 주문을 다시 잠가 본인·계정·기한·결제 식별 정보와 매장/품절을 확인한다. 조회 중 먼저 접수됐으면 VIEW_ORDER를 반환한다. 만료 기한을 늘리지 않는다.

재개 결과의 `data.payment.portOne`은 최초 주문 생성과 동일한 형식이다. 재개 요청 직후에도 상태가 바뀔 수 있으므로 SDK 결과·웹훅·서버 확인을 통해 최종 결과를 확인한다. 같은 paymentId의 실제 KG이니시스 실패/이탈 후 재개는 테스트 채널에서 추가 검증한다.

설계 참고: [PortOne SDK paymentId 요청 규칙](https://developers.portone.io/sdk/ko/v2-sdk/payment-request?v=v2), [서버 결제 조회·검증](https://developers.portone.io/opi/ko/integration/start/v2/checkout).

## 실행 검증

Java 21, Docker/Colima에서:

```sh
./mvnw --batch-mode --no-transfer-progress -Duser.timezone=UTC test
```

Colima 사용 시 JAVA_HOME(Java21), DOCKER_HOST, TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE를 README 방식으로 지정한다.
`DevOrderFlowIntegrationTest`는 실제 로그인·주문 생성·승인·사장님 처리·픽업 API를 호출하며, 주문 상태를 테스트 픽스처로 강제 승격하지 않는다.
`DevPaymentActivationTest`는 dev opt-in·prod 제외 조건을, `PersistentFakePaymentServiceTest`는 실제 게이트웨이 모드에서 가짜 승인이 막히는지 검증한다.

2026-11-24 결제창 재개·결제 상태 조회 통합 검증: Java 21·Colima의 실제 MySQL 8.4·UTC JVM 전체 388건 통과, 실패·오류·스킵 0.
주문 생성·가짜 승인부터 고객 취소/매장 거절·전액 환불 상태 조회까지 검증했다.
동시 수락·취소·거절, 중복 거절 재조회, 권한·비활성 직원, PG 취소의 주체·금액·멱등 키도 포함한다.
만료 후 앱 확인·웹훅·주기 재조회, 동시 복구, 리스 만료·오래된 결과 차단, 미결제·일시 오류 재조회와 환불 저장 실패 롤백을 포함한다.
결제창 재개는 PG 미등록·READY·FAILED·PAID·진행 중·취소·정보 불일치, 인증·계정·매장/품절, 조회 중 접수·만료·계정 정지 및 동시 요청을 포함한다. 상태 조회의 FAKE 지원·환불 FAILED 표시와 서버 내부 정보 제외도 검증했다.
실제 KG이니시스 테스트 채널 환불과 앱 실기기 연동은 아직 검증하지 않았다.

아래 결과는 초기 개발용 가짜 결제의 검증 기록이다.

최종 검증(2026-11-10): 전체 180건 통과, 실패·오류·스킵 0. MySQL 8.4/UTC JVM, 모듈 경계 검사 포함.
`docker compose config --quiet`, `sh -n docker/dev-seed/run.sh`, `git diff --check`도 통과했다.
실제 Compose 컨테이너를 새로 배포하거나 고객/사장님 앱 기기에서 실행한 결과는 아니다.
