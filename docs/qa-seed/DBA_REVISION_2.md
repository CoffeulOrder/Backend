> 2026-11-26 인계 갱신: 동현이 실제 DB 이름을 `coffeul`로 확인했다. 최신 SQL·기준 시각·적용 순서는 [동현 인계 문서](../../qa-data/android/2026-11-26/README.md)를 우선한다. 아래 DB 이름 미확인/검토본 설명은 당시 기록이며, 원격 적용 완료를 뜻하지 않는다.

# DBA 추가 요청 반영 · 개정 2

기존 고정 매장·메뉴·계정·주문 ID는 유지한다. 기존 학교/메뉴 총수 3/157, S01 DB 메뉴 48개도 유지한다. 신규 고객 4개와 주문 13건을 추가한다. 사용자에게 받은 실제 격리 DB 이름으로 최종 산출물을 생성해야 하며, 외부 공개 서버에는 적용하지 않는다.

## 열린 매장과 계정

- qa-heavy 총 150건 유지: heavy-001~080은 S01 OPEN, 081~150은 S02 PAUSED. 완료/취소/거절은 전체에서 각 50건이다.
- S01의 heavy 주문에 줄 수 1/2/10/20, 수량 1/20, 옵션 없음/있음/긴 문구를 혼합했다. 현재 정상인 메뉴·옵션으로 구성하고 주문 당시 이름·가격·선택 ID를 manifest의 reorderItems에 기록했다.
- page20/page21/filtered는 각각 20/21/30건과 상태·최신순 구조를 유지하며 S01로 이동했다.
- wd-block 1건도 S01로 이동. 이에 따라 S01 대기 건수는 7 → 8이다. S02 대기는 여전히 0이다.
- qa-multistore는 S01 2건, S08 2건, S02 1건의 COMPLETED 주문이다.
- qa-longname-ko/en은 한글/영문 이름이 각각 정확히 20자다. S11은 OPEN·메뉴 0개를 유지하며 qa-s11 DB 계정은 만들지 않았다.

## 재주문 R1~R8

별칭과 실제 주문 ID·메뉴/옵션 ID·원래 가격은 manifest.json의 reorderCases, orders와 reorder-cases.json에 있다. 계정은 qa-reorder@g.eulji.ac.kr이다. 모든 R 주문은 COMPLETED다.

| 별칭 | 전용 데이터·조건 |
| --- | --- |
| R1 | M0122·M0123, 2줄 전부 품절 |
| R2 | M0124·M0125·M0126, 3줄 중 M0124만 품절 |
| R3 | M0127 HIDDEN + M0128 정상. 실제 삭제 대신 비노출; 주문 항목 FK 보존. 숨긴 메뉴 상세는 404 |
| R4 | M0129. 주문 스냅샷은 원래 가격, 현재 기본가는 +1,000원 |
| R5 | M0130. 주문한 옵션만 품절, 대체 옵션은 정상 |
| R6 | 준비 중인 S02 주문 |
| R7 | S01 정상 메뉴 20줄. 첫 줄 M0101 수량20. 기존 장바구니 M0101 수량1을 더하면21 |
| R8 | 다른 OPEN 매장 S08 주문 |

M0122~M0130은 다른 고객의 시드 주문에 사용하지 않는 전용 영역이다. M0101~M0121 및 기존 옵션 O1~O11은 이 시나리오 때문에 품절/비노출/가격 변경하지 않는다. S01 DB 행 수는 48이지만 R3의 HIDDEN 1개 때문에 일반 메뉴 목록은 47개다. 직접 품절은 기존6+전용3=9개이며 여전히 담을 수 있는 메뉴는 25개 이상이다.

SQL은 전용 메뉴/옵션의 원래 상태를 upsert → 주문 스냅샷 작성 → 전용 품절/비노출/인상 적용 순서다. seed_all과 reset_consumables 모두 같은 R1~R5 시험 상태를 복구한다. 가격이 실행 때마다 누적 인상되지는 않는다.

기존 데이터에서 재생성할 때 주문 내부 항목/이력 ID 배치가 달라질 수 있어, 소유가 확인된 QA 주문의 하위 데이터만 재구성한다. 고객·메뉴·주문 ID는 그대로다. 최초 v1 SQL 적용과 시험 주문 생성 후 v2를 적용하는 업그레이드도 격리 MySQL에서 검증한다. 기존 사용자가 만든 비QA ID/자연키는 충돌 시 중단한다.

## 재주문 API 계약의 확인 사항

현행 OrderDetailResponse.Line은 menuName/options/unitPrice/quantity/lineAmount만 반환하고 menuId·optionItemId는 반환하지 않는다. DB에는 참조 ID가 있으므로 응답 확장은 가능하다. **앱이 이름만 보고 과거 메뉴·옵션을 추정하면 가격 변경/중복 이름/비노출 처리에 취약하다.**

R3 검증에서 HIDDEN 목록 노출·직접 주문 허용 결함을 확인해 ACTIVE 필터 최소 수정을 별도 PR로 준비했다. 그 외 주문 상세 ID·좌표·재개시각 등 응답 확장은 구현하지 않았다. manifest.reorderItems는 QA 검증용 원본이다. 실제 재주문 연결은 FE가 식별 ID를 보존하는지 확인하고, 필요하면 주문 상세에 menuId·선택 옵션 ID/스냅샷 이름을 추가하는 계약을 정한다. 새 장바구니의 가격·품절·필수 옵션은 현재 메뉴 API와 주문 생성 API로 다시 검증한다.

## 요청한 컬럼·응답 확장 가능 여부

아래는 가능성 검토이며 구현/배포 완료가 아니다.

| 요청 | 현재 구조 | 가능한 방법 |
| --- | --- | --- |
| 좌표(위도·경도) | store에 없음 | nullable latitude/longitude 컬럼과 -90~90/-180~180 제약 추가 후 DTO 노출. 좌표만으로 직선거리는 계산 가능하지만 정확한 도보 시간은 경로 서비스가 별도로 필요 |
| 영업 재개 시각 | store에 없음 | UTC DATETIME(6) nullable next_open_at과 reopensAt 응답 제안. PAUSED/CLOSED에서 표시, null이면 미정. 시간이 됐다고 자동 OPEN으로 바뀌는 기능은 별도 |
| 인기 isPopular | 없음, is_event와 별개 | 매장별 최근7일 COMPLETED 주문의 판매 수량 합계 TOP5를 일괄 조회/캐시하거나 인기 메뉴 조회 API 제공. 동률은 menuId 등으로 고정. 기준 합의 필요 |
| 목록 주소 | seller.business_address, store.location 존재 | 사업장 주소와 매장 주소가 같은지 먼저 확인. 다르면 store.address 신규 컬럼 필요. location을 정식 주소로 단정하지 않음 |
| 목록 학교명 | school.name/campus 존재 | 고객 학교 권한을 유지한 join/일괄 조회로 응답 확장 가능. 새 컬럼 불필요 |
| 목록 메뉴 수 | menu_item 존재 | ACTIVE 메뉴 수를 매장별 집계해 menuCount에 포함 가능. 품절 메뉴 포함 여부를 정하고 orderableMenuCount와 구분 |
| 목록 대기 건수 | orders 존재 | 현재 aheadCount와 일치하게 REQUESTED/ACCEPTED/MAKING 집계 제안. READY 포함 여부는 별도 정책이며 이번 검증의 S01 기대값은8 |

학교 권한과 N+1 방지를 유지해 한 번에 집계해야 한다. 운영 설정·키·DB 접속 자격증명은 이 작업에서 읽지 않는다.

## 수신 확인 자료

- data/accounts.csv, orders.csv, manifest.json, reorder-cases.json
- data/verify.sql 및 reports/verify-results.json/CSV (격리 MySQL에서 실제 actual/expected)
- manifest.baseTime, targetDatabase, targetEnvironment, revision=2
- reports의 검증 DB는 로컬 격리 Testcontainers다. 원격 적용/검증 결과로 표현하지 않는다.

실행 도구는 변이 SQL의 --execute에 --confirm-isolated-target을 요구한다. 담당자가 mysql login-path를 직접 준비하고 격리 DB임을 확인한다. 이 확인 옵션만으로 실제 네트워크 격리를 자동 증명하는 것은 아니다. 기본 명령은 파일만 생성하며 원격 DB에는 접속하지 않는다.


## 숨김 메뉴 수정과 DB 이름 확인 상태

R3는 기존 서버에서 메뉴 상세만 404이고 고객 목록/주문 생성은 HIDDEN을 무시하는 결함이 있었다. [초안 PR #26](https://github.com/CoffeulOrder/Backend/pull/26)의 수정이 배포돼야 목록47·새 주문404/MN001이 실제 서버에도 적용된다. 데이터 SQL만 실행해 이 서버 코드 결함이 수정되지는 않는다. 수정은 아직 머지/배포하지 않았다.

현재 전달 인프라 문서와 GitHub DB 이름 변수 조회로 실제 격리 DB 이름을 확정하지 못했다. AWS/DB 조회 연결도 없다. .env·AWS 자격증명·PG 키는 열람하지 않았다. 검토본의 manifest.targetDatabase는 null이며 SQL의 DB_NAME_REQUIRED는 실행을 막는 표식이다. 담당자는 접속한 격리 DB에서 `SELECT DATABASE();` 결과의 이름만 확인해 --database로 다시 생성한다. 암호나 키는 전달할 필요가 없다. 이 확인 전까지 실제 DB 적용을 완료했다고 표현하지 않는다.
