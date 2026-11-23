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
- 테스트 채널에서 실제 PG 조회·확정과 서명된 웹훅 수신, 고객 취소·매장 거절의 실제 전액 환불을 검증한다. 만료된 결제의 대사·복구, 결제 재시도, 수락 후 매장 환불은 후속 작업이다.
- 고객 앱의 메뉴 상세·옵션 조회는 `GET /api/v1/stores/{storeId}/menus/{menuId}`와 연동해 기기에서 검증한다.
- 실제 고객 앱·사장님 앱의 서버 연결과 기기 검증, CORS, 푸시 실제 발송.
- 기본 주문 상태는 기존 ACCEPTED/MAKING 분리를 유지한다. 닉네임·PIN·미수령·REFUNDED·관리자 웹 정책은 별도 합의 후 반영한다.

## PortOne V2 KG이니시스 MS-16·27

개발 검증 환경에서 `coffeul.payment.gateway=portone`을 선택한다. `coffeul.payment.portone.api-secret`에는 해당 PortOne 계정의 V2 API Secret을 서버에만 주입한다. 현재 어댑터는 서버 인스턴스당 한 PortOne 계정의 Secret을 사용한다.

매장에 연결된 `merchant_pg`에 `pg_provider=INICIS_V2`, `status=ACTIVE`, `portone_store_id`(PortOne 상점 ID), `client_key`(결제창 채널 키)가 있어야 한다. `mid`는 KG이니시스 식별자다. V3는 상점 ID와 결제 시도에 보관할 상점·채널 컬럼을 추가한다.

1. `POST /api/v1/orders`에 고객 Bearer와 `Idempotency-Key`를 보낸다. `payment.portOne`의 `storeId`, `channelKey`, `paymentId`, `currency`와 `payment.orderName`, `payment.amount`로 결제창을 연다. 구매자 이름은 고객 앱에서 전달한다. 같은 주문 생성 요청은 저장된 첫 결제 시도의 정보를 반환한다.
2. 결제창 복귀 후 고객 Bearer로 `POST /api/v1/payments/confirm`에 `{ "orderId": 1, "paymentId": "<결제 식별자>" }`를 보낸다. `orderId`는 주문 생성 응답값이다. 서버는 PortOne에서 `PAID` 여부, 결제 ID·상점·채널·PG·통화와 저장된 주문 금액을 확인한다.
3. 결제 승인·주문 접수·픽업번호·이력을 한 트랜잭션으로 저장한다. 확인 요청을 다시 보내면 이미 접수된 주문의 현재 상태를 반환한다.

PortOne 조회 지연·오류는 `PY010`, 결제 미확인은 `PY009`, 결제 식별 정보 불일치는 `PY011`, 금액 불일치는 `PY001`로 응답한다. 이미 PortOne에서 결제된 주문도 로컬 주문이 만료됐거나 접수 불가하면 주문을 접수하지 않는다. 고객의 수락 전 취소·환불은 아래 MS-19를 따른다. 만료 후 결제된 주문의 대사·복구와 테스트 채널 검증은 운영 결제 오픈 전에 완료한다.

### PortOne V2 웹훅

`coffeul.payment.gateway=portone` 환경에서 `coffeul.payment.portone.webhook-enabled=true`로 수신 경로를 켜고, 콘솔에서 발급한 웹훅 시크릿을 `coffeul.payment.portone.webhook-secret`에 서버 설정으로 주입한다. 테스트 모드에서 PortOne이 접근할 수 있는 HTTPS 주소의 `POST /api/v1/payments/webhooks/portone`을 등록하고 V2의 `2024-04-25` 본문 형식을 사용한다. [PortOne 웹훅 연동](https://developers.portone.io/opi/ko/integration/webhook/readme-v2?v=v2)

- 요청 본문 원본과 `webhook-id`, `webhook-timestamp`, `webhook-signature`로 서명을 검사한다. 발송 시각이 서버 시각과 5분 넘게 차이 나거나 서명이 유효하지 않으면 `401`을 반환한다.
- `Transaction.Paid`가 오면 저장한 상점·결제 식별자를 대조하고 PortOne API에서 조회한다. 앱 확인 API와 같은 트랜잭션으로 주문을 접수한다. 중복 알림과 앱 동시 요청은 접수 이력·픽업번호를 한 번만 만든다.
- 처리 완료, 이미 처리한 결제, 등록되지 않은 결제 식별자, 그 외 이벤트는 `204`로 응답한다. PG 일시 오류는 `503`으로 응답하며 주문은 다음 전달에서 다시 확인할 수 있다. 만료된 주문은 접수를 차단하며, 이미 결제된 만료 주문의 대사·환불은 후속이다.

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

## 실행 검증

Java 21, Docker/Colima에서:

```sh
./mvnw --batch-mode --no-transfer-progress -Duser.timezone=UTC test
```

Colima 사용 시 JAVA_HOME(Java21), DOCKER_HOST, TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE를 README 방식으로 지정한다.
`DevOrderFlowIntegrationTest`는 실제 로그인·주문 생성·승인·사장님 처리·픽업 API를 호출하며, 주문 상태를 테스트 픽스처로 강제 승격하지 않는다.
`DevPaymentActivationTest`는 dev opt-in·prod 제외 조건을, `PersistentFakePaymentServiceTest`는 실제 게이트웨이 모드에서 가짜 승인이 막히는지 검증한다.

2026-11-23 취소·거절 통합 검증: Java 21·실제 MySQL 8.4·UTC JVM 전체 326건 통과, 실패·오류·스킵 0.
주문 생성·가짜 승인부터 고객 취소/매장 거절·전액 환불 상태 조회까지 검증했다.
동시 수락·취소·거절, 중복 거절 재조회, 권한·비활성 직원, PG 취소의 주체·금액·멱등 키도 포함한다.
실제 KG이니시스 테스트 채널 환불과 앱 실기기 연동은 아직 검증하지 않았다.

아래 결과는 초기 개발용 가짜 결제의 검증 기록이다.

최종 검증(2026-11-10): 전체 180건 통과, 실패·오류·스킵 0. MySQL 8.4/UTC JVM, 모듈 경계 검사 포함.
`docker compose config --quiet`, `sh -n docker/dev-seed/run.sh`, `git diff --check`도 통과했다.
실제 Compose 컨테이너를 새로 배포하거나 고객/사장님 앱 기기에서 실행한 결과는 아니다.
