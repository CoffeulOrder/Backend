# 요청 대비 구현·제약·Q1~Q10 답변

개정 2는 DBA_REVISION_2.md를 우선한다. 기존 아래 기록의 S01 대기7/직접품절6은 개정2에서 8/9로 바뀌며, 고객40·주문301이 된다. 기존 검증 수치는 당시 기록이다.

BE_GUIDE_MAPPING_M8.md 원본은 전달된 첨부/작업 공간에서 찾지 못해 대조하지 못했다. 실제 컨트롤러·DTO를 기준으로 작성했다.

기준: 2026-11-25, `develop f603843`, MySQL 8.4, Flyway V1~V4. **이 문서는 실제 코드·제약을 확인한 결과다.** 태완 원문에 빠진 O1~O11은 사용자 확인을 받아 아래 권장 조합으로 확정했고, 카테고리 합계 44개는 커피 4개 추가로 48개에 맞췄다.

## 데이터 범위와 ID

모든 개인·사업자·결제 정보는 가상이다. 가상 사업자번호는 `0000000001` 등의 유효하지 않은 시험 문자열이다. 실제 카드번호는 넣지 않는다. A 학교는 기존 `g.eulji.ac.kr` 도메인의 학교 행을 읽기만 하며 수정하지 않는다. B/C 가상 학교와 도메인 `qa-b.example`, `qa-c.example`을 추가한다. 학교 수 3은 이 **QA 범위의 학교 수**이며 기존 DB 전체가 반드시 3개라는 뜻이 아니다.

`S01~S11`은 학교 A, `S12`는 B, `S13`은 C다. `qa-school-b/c`로 학교 격리를 확인한다. A 고객에게 13개 매장이 모두 보이면 오히려 문제다. 실제 PK는 고정 BIGINT이고 별칭은 `manifest.json`, CSV에 매핑한다. 기존 앱에서 숫자 ID를 32비트 Int로 선언했다면 Long으로 맞춰야 한다.

시드가 만든 ID·별칭은 `ormo_qa_seed_registry` 개발 보조 테이블에 기록한다. 스키마 마이그레이션이나 애플리케이션 동작을 바꾸지 않는다. 첫 적용 때 비QA PK/자연키와 충돌하면 실패하고, 재실행은 같은 ID를 upsert한다. 초기화 중에는 QA 앱을 닫는다. 초기화는 QA 고객의 기준 주문을 복구하고 QA 고객이 시험 중 만든 주문을 제거한다. 다른 고객의 주문이나 실매장 설정은 대상으로 삼지 않는다. PK/자연키 충돌과 잘못된 DB 대상 중단은 실제 MySQL에서 검증한다.

## 매장·카테고리·메뉴

| 요청 | 결과 |
| --- | --- |
| 학교 3 / 매장 13 | QA 범위에서 구성. S11은 메뉴 0개 |
| S01 48개 | 커피 18 / 티·음료 10 / 디저트 8 / 베이커리 6 / 시즌 4 / 빈 이름 2 / 빈 카테고리 0 |
| S01 카테고리 7 | 이름 빈 카테고리 1개, 메뉴 0개 카테고리 1개. 긴 이름 1개 |
| 타 매장 수 | S02 20, S03/04 12, S05 15, S06 10, S07 8, S08 3, S09 6, S10 5, S11 0, S12 10, S13 8 |
| 품절·장바구니 | S01 직접 품절 6개 + 필수 옵션 전부 품절 1개. 신규·이벤트·품절의 8가지 Boolean 조합 포함. 담을 수 있는 메뉴 최소 25개 이상. S09 6개 전부 품절 |
| 신규 경계 | M0112 오늘, M0113 13일 전, M0114 14일 전. 시드는 나이 <14일을 is_new=true로 설정 |
| 이미지 | S01 null 4개(M0143~46), 의도한 404 2개(M0147~48). 정상 HTTPS placeholder URL 30개 |
| 인기 | 최근 7일 완료 주문에 S01 메뉴 5개를 넣고 집계 쿼리 제공. isPopular 응답/자동 배치는 없음 |
| 빈 매장·카테고리 숨김 | 현재 BE는 자동 숨기지 않는다. FE의 규칙 적용 여부를 확인해야 함 |

이미지 키는 절대 URL이 아니라 `seed/ormo-qa-01/480/360.jpg` 같은 상대 경로다. 현행 `MenuQueryService`가 `coffeul.cdn.base-url + '/' + image_key`로 합친다. QA 서버에 base-url을 `https://picsum.photos`로 설정해야 실제 이미지가 열린다. 다른 매장의 기존 이미지 키에 영향을 줄 수 있으므로 서버 담당자가 적용 대상을 확인한다. 파일 생성만으로 현재 서버 CDN 설정이 바뀌지 않는다. [Picsum 공식 안내](https://picsum.photos/)의 고정 seed URL을 사용했다. 30개 정상 URL과 2개 404 결과는 별도 검증 JSON에 남긴다. placeholder 사진은 실제 메뉴 사진이 아니다.

영업 표현은 문서의 “6종”과 달리 OPEN 1 + PAUSED 3 + CLOSED 3 = **7개**다. 저장하는 상태값은 3개이며 재개 시각 변형은 아래처럼 FE 전용 파일에 별도 제공한다.

| 매장 | 실제 DB 상태 | 재개 시각 요청 | 실제 지원 |
| --- | --- | --- | --- |
| S01 | OPEN | 없음 | 상태 지원 |
| S02 | PAUSED | 기준 +35분 | 재개 시각 컬럼/응답 없음 |
| S03 | PAUSED | 내일 08:30 | 동일 |
| S04 | PAUSED | 없음 | 상태 지원 |
| S05 | CLOSED | 기준 +35분 | 재개 시각 컬럼/응답 없음 |
| S06 | CLOSED | 내일 08:30 | 동일 |
| S07 | CLOSED | 없음 | 상태 지원 |

S01 평일 08:30~18:00, 토 10:00~15:00, 일 휴무. S02 매일 운영, S05 월·수·금만 운영, S07 전 요일 휴무다. **공휴일 달력은 없다.** 주문 가능 여부는 영업시간이 아니라 store.status다.

- S10의 22:00~02:00은 현행 CHECK(open_time < close_time)에 걸린다. DB에는 22:00~23:59:59 대체값, 요청 원본은 FE mock에 따로 기록한다. 실제 자정 넘김 지원으로 표시하지 않는다.
- S08은 시작 시각만 있고 끝 null인 행을 `is_closed=true`로 넣는다. 휴무가 아니면 DB가 거부한다.
- 판매자: S01 전부, S02 통신판매번호 null, S05 250자 긴 주소는 지원한다. S03 전화 null, S04 사업자번호 null, S10 상호·대표만 있음은 NOT NULL 제약 때문에 DB에 넣지 않고 FE mock에만 둔다.
- 좌표·거리·도보시간 필드는 없다. 실제 캠퍼스 좌표를 임의로 만들거나 저장하지 않았다.

## O1~O11 옵션 조합

| 조합 | 메뉴 | 내용 |
| --- | --- | --- |
| O1 | M0101 | 옵션 없음 |
| O2 | M0102 | 필수 단일 온도 HOT/ICE |
| O3 | M0103 | 선택 단일, 선택 해제 가능 |
| O4 | M0104 | 필수 복수 최소 1 / 최대 2 |
| O5 | M0105 | 선택 복수 최대 3 |
| O6 | M0106 | “제한 없음” 요청 대체: 선택지 6개 / 최대 6. 진짜 무제한 값은 미지원 |
| O7 | M0107 | 필수 단일, 첫 기본값 R은 품절 / L은 정상 |
| O8 | M0108 | 필수 그룹 모두 품절: FE가 메뉴 전체 품절로 표시하는지 확인 |
| O9 | M0109 | 선택 그룹 모두 품절: 선택 없이 메뉴 주문 가능 |
| O10 | M0110 | 온도 + 사이즈 + 추가 6개. 긴 선택 문구 |
| O11 | M0111 | 선택 단일에 isDefault=true 2개. 방어 데이터; 두 개를 함께 주문하면 maxSelect 위반 |

기본 선택을 자동 수행하는 것은 FE다. BE는 실제 전달받은 ID와 min/max를 검증하며 기본값을 대신 채워주지 않는다. minSelect=1인 그룹에 아무것도 보내면 OD003이다. 선택지는 0원·양수 추가금을 포함한다. 음수 추가금은 DB가 거부한다. 필수 전체 품절은 목록의 soldOut 필드에 자동 합산되지 않으므로 FE가 상세 옵션으로 확인해야 한다.

## 주문·결제·페이지

| 요구 | 실제 구성 |
| --- | --- |
| 주문 상태 | BE 9개 각각 4건 이상. 환불은 별도 payment_cancel 3상태 |
| 줄/수량 | 1/2/10/20줄, 줄당 1/2/20개, 합계 50개 이상 사례 |
| 이름/옵션 | 30자 이상 스냅샷, 옵션 없음/ICE·샷 추가/6개 이상 옵션, 각 옵션은 해당 메뉴 소속 FK |
| 금액 | 500 / 3,500 / 1만 이상 / 10만 이상. 항목·옵션·주문·결제·환불 합계 일치, 할인 0 |
| 픽업 | 1 / 27 / 100 / 999 / null. orderCode와 다른 값. 매장·영업일 중복 없음 |
| 시각 | 오늘/어제/7일 전/한 달 전/작년 12월31일 23:59/올해1월1일 00:00/7개 요일 |
| 메모 | null / 짧은 메모 / 정확히 100자 / 4바이트 이모지·줄바꿈. **100자 초과는 DB 미지원** |
| 타임라인 | 단계별 시각, 일부 acceptedAt 누락 사례 |
| 결제·환불 | READY/APPROVED/FAILED/UNKNOWN/CANCELED, 환불 REQUESTED/SUCCEEDED/FAILED |
| 다중 시도 | 실패4+승인1, 실패5. GET payment는 최신 시도만 반환하며 전체 시도 목록 API는 없음 |
| 보상 | EXPIRED + SYSTEM 전액 환불 완료. 미접수 REJECTED 보상은 #21/V5 필요 |
| 간편결제 표시 | CARD/KAKAOPAY/NAVERPAY/TOSSPAY 문자열. 실제 간편결제 연동 성공이라는 뜻이 아님 |
| 페이지 | heavy 150 / page20 20 / page21 21 / filtered 최신25 EXPIRED + 과거5 COMPLETED |
| 실시간 | live1/3/5와 wd-block. S01 대기 7, S02 0. 매장 목록 자체에는 대기수 필드 없음 |

실제 카드번호 마스킹/승인번호, 포장/매장 섭취, 수령 기한, UNCLAIMED·REFUNDED 주문 상태는 DB/API에 없다. 이 값은 `frontend_mock_cases.json`에 **실제 API 계약이 아닌 방어 예시**로 명확히 구분했다. “환불 완료” 화면은 주문 CANCELED/REJECTED/EXPIRED와 refund SUCCEEDED를 조합한다.

일반 주문 생성은 20분 만료다. 시드의 진행 중 화면이 금방 끊기지 않도록 이미 생성된 QA PENDING_PAYMENT에만 기본 8시간을 준다. `--session-hours`로 바꿀 수 있고 매일 재계산한다. 기존 DB에 수령 만료 필드는 없어 만들어 넣지 않았다. 자동 만료·환불 스케줄러가 동작하면 fixture도 바뀔 수 있다. REQUESTED 환불의 next_retry_at은 QA 세션 뒤로 잡아 화면 확인 시간을 준다. 실제 PG 성공으로 표시하지 않는다.

## Q1~Q10 답변

| 질문 | 확인 결과·다음 담당 작업 |
| --- | --- |
| Q1 매장 목록 확장 | 현재는 storeId/name/location/status. seller 주소·school.name은 별도 테이블, 메뉴 수·대기수는 집계 가능하지만 목록에 미노출. 좌표·재개시각은 컬럼부터 없음. 신규 응답 계약과 BE 작업 필요 |
| Q2 상태 전이 | PENDING_PAYMENT → REQUESTED → ACCEPTED → MAKING → READY → COMPLETED. REQUESTED에서만 고객 취소 CANCELED / 매장 거절 REJECTED. 만료 EXPIRED. 환불은 REQUESTED/SUCCEEDED/FAILED 별도. UNCLAIMED 없음 |
| Q3 인기/이벤트 | is_event와 is_new는 저장 Boolean. isPopular/7일 인기 자동 계산 없음. QA 7일 TOP5 쿼리만 제공 |
| Q4 섭취/포장 | 생성 DTO·DB에 없음. 임의 필드를 넣어 지원했다고 하지 않음 |
| Q5 인증코드 | 기본 발송은 난수. log 메일 어댑터가 있음. QA 전용 prepare_verification으로 123456의 SHA-256만 넣고 실제 confirm을 거쳐 토큰 발급. 일반 코드 생성기·인증 검증은 변경하지 않음 |
| Q6 직원/진행 | qa-owner-s01~13 및 qa-staff-s01. 직원 로그인은 POST /api/v1/auth/staff/login {loginId,password}. 아래 정방향 API 또는 advance_order SQL 제공 |
| Q7 DEV-1/실패/지연 | 기존 dev+fake+활성 설정에서 confirm 가능. 이 작업은 서버를 전환하지 않음. 실패5/UNKNOWN/환불실패는 DB 조회 fixture. FAKE에서 PY009/12/13을 실제 PortOne 오류처럼 강제하는 기능은 없음. PortOne 테스트/FE mock 필요 |
| Q8 음수/기본값 | price_delta>=0 제약으로 음수 불가. 기본값 중복을 막는 DB 제약은 없어 O11로 방어 시험 가능. 선택 수 검증은 별도 |
| Q9 점검 모드 | 503/MAINTENANCE/종료 시각 지원 없음. mock 응답은 제공. 실제 설정 스위치로 소개하지 않음 |
| Q10 이미지 | 30개 Picsum HTTPS placeholder와 2개 404 URL. S3/CDN 업로드 권한·버킷은 열람하거나 변경하지 않음. 실제 메뉴 사진 교체는 별도 |

직원 상태 변경은 `POST /api/v1/staff/orders/{orderId}/accept`, `/start`, `/ready`, `/complete`다. `qa_api.py`는 QA 로그인·토큰을 메모리에서만 다루며 토큰을 출력하지 않고 리다이렉트를 거부한다. manifest에 없는 주문은 다루지 않는다. SQL 진행은 at을 오프셋 포함 시각으로 받아 타임라인·이력을 채운다. **SQL로 바꾸면 푸시·도메인 이벤트는 발행되지 않는다.**

Q5 시나리오: prepare valid 후 confirm 123456은 정상, wrong 후 confirm 654321은 EV004, expired 후 confirm은 EV005. resend를 준비한 직후 발송 API를 부르면 EV002, daily를 준비하고 발송하면 EV003이다. 일일 한도는 기본 10회·KST 자정 기준이다. 서버 설정을 바꿨다면 같은 한도로 준비하는 추가 조정이 필요하다. 자정 직후 60초 이내는 재발송 제한 EV002가 먼저여서 EV003 시험은 잠깐 기다린다. 이 오류 시험은 발송이 차단되므로 실제 메일을 보내지 않는다. 성공 발송을 시험할 때는 개발 log 메일 설정을 담당자가 확인한다.

가입용 예약 주소가 실제 가입되면 reset에서 주문·토큰·동의와 함께 제거해 다시 미가입 상태로 만든다. **가입 직후 탈퇴까지 같은 계정으로 섞으면 이메일이 지워져 추적할 수 없으므로**, 탈퇴는 별도 고정 wd 계정 5개를 사용한다. 탈퇴된 익명 이력까지 무차별 검색·삭제하지 않는다.

## 시간·검증

DB DATETIME은 UTC로 생성하고 SQL 세션 +00:00, 문자셋 utf8mb4를 확인한다. 주문 상세·타임라인/사장님 주문 시각은 +09:00 응답을 실제 테스트한다. 결제 상태 조회 Result의 Instant(expiresAt/approvedAt 등)는 Z로 직렬화될 수 있다. 앱은 오프셋 또는 Z로 파싱해 Asia/Seoul로 변환한다. 공통 timestamp는 서버 기본 오프셋에 따르므로 항상 +09:00이라고 가정하지 않는다.

로컬 결과는 VALIDATION.md에 기록한다. 이 검증은 격리 MySQL과 실제 서버 코드/MockMvc 결과이며, 원격 DB 적용·실제 Android 화면·실제 PG 성공을 확인한 것은 아니다. `verify.sql` 결과를 원격 담당자가 확인한 뒤 그 환경의 “데이터 준비됨”으로 판단한다.

DB 담당자가 줄 수·수량·픽업 경계까지 직접 확인할 때의 추가 읽기 쿼리:

```sql
SELECT line_count, COUNT(*) AS orders_in_case
FROM (SELECT o.id, COUNT(l.id) AS line_count
      FROM orders o JOIN ormo_qa_seed_registry r ON r.table_name='orders' AND r.entity_id=o.id AND r.fixture_version='ormo-android-qa-v1'
      JOIN order_line l ON l.order_id=o.id GROUP BY o.id) cases
GROUP BY line_count;
-- line_count: 1, 2, 10, 20
SELECT MIN(l.quantity) AS min_quantity, MAX(l.quantity) AS max_quantity
FROM order_line l JOIN ormo_qa_seed_registry r ON r.table_name='orders' AND r.entity_id=l.order_id AND r.fixture_version='ormo-android-qa-v1';
-- min 1 / max 20
SELECT COUNT(DISTINCT o.pickup_no) AS four_pickup_boundaries
FROM orders o JOIN ormo_qa_seed_registry r ON r.table_name='orders' AND r.entity_id=o.id AND r.fixture_version='ormo-android-qa-v1'
WHERE o.pickup_no IN (1,27,100,999);
-- 4. PENDING_PAYMENT / EXPIRED 사례에는 별도로 pickup_no NULL 존재
SELECT l.order_id, SUM(l.quantity) AS total_quantity
FROM order_line l JOIN ormo_qa_seed_registry r ON r.table_name='orders' AND r.entity_id=l.order_id AND r.fixture_version='ormo-android-qa-v1'
GROUP BY l.order_id HAVING total_quantity>=50 LIMIT 5;
```
