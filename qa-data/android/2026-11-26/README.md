# Android QA 더미데이터 · 동현 인계 · 2026-11-26

DB 이름은 동현이 `coffeul`로 확인했다. 이 브랜치는 SQL·합성 QA 계정표·재생성 도구만 전달한다. 원격 DB 적용은 동현이 담당하고, PortOne 전환은 태완과 일정 협의 후 진행한다. 이 작업에서 서버 설정·PortOne 키·원격 DB는 변경하지 않았다.

- 대상 DB: `coffeul` / 생성 환경: `dev`
- 생성 기준 시각(baseTime): `2026-11-26T12:37:15+09:00`
- 기준 스키마: `develop f603843`, Flyway V1~V4
- 범위: 매장13, 메뉴157(S01 DB48), 고객40, 직원14, 주문301. 학교 A는 기존 학교를 참조하고 B/C 2개만 추가한다.
- `qa-heavy`: 전체150건 중 S01(OPEN)80/S02(PAUSED)70. page20/page21/filtered/wd-block은 S01. S01 대기8.
- `qa-reorder` R1~R8, 다중 매장, 한글·영문20자 이름 포함. [재주문 사례](data/reorder-cases.json) · [주문 ID](data/reorder-order-ids.csv)

## 1. 적용 전에

앱을 닫고 격리된 개발/스테이징 DB임을 담당자가 확인한다. QA 계정의 공개 공통 비밀번호를 쓰므로 실운영·외부 공개 공유 서버에는 적용하지 않는다. DB 이름이 같다는 것만으로 격리를 보증하지 않는다.

DB에 기존 Flyway 마이그레이션과 `g.eulji.ac.kr` 학교 도메인이 있어야 한다. 로그인 경로는 동현이 자신의 환경에 준비한다. 비밀번호나 접속 키를 Git·대화에 넣지 않는다. 아래 `ormo-qa`는 예시 login-path 이름이다.

## 2. 적용 직전에 재생성하고 적용

```sh
python3 tools/qa-seed/qa_seed.py seed_all --database coffeul --environment dev --output-dir qa-data/android/2026-11-26/data --execute --confirm-isolated-target --mysql-login-path ormo-qa
```

이 명령은 실행 시점의 KST 기준으로 파일을 다시 생성한 뒤 적용한다. 확인된 QA 소유 범위만 초기화하며 비QA ID·자연키 충돌이나 다른 DB 이름이면 중단한다. 고정 ID는 유지한다. 재실행하면 QA 계정·시험 주문이 초기화되므로 시험 중인 앱과 협의한다.

SQL만 직접 실행할 경우, **아래 생성 시각의 시험 기한이 아직 유효할 때** 다음 두 파일을 순서대로 사용한다. `mysql --force`는 사용하지 않는다.

```sh
mysql --login-path=ormo-qa --default-character-set=utf8mb4 --batch --database=coffeul < qa-data/android/2026-11-26/data/seed_all.sql
mysql --login-path=ormo-qa --default-character-set=utf8mb4 --batch --database=coffeul < qa-data/android/2026-11-26/data/verify.sql
```

진행 중 주문의 시험 기한은 baseTime 기준8시간이다. 늦게 적용하면 위 재생성 명령을 우선한다. 영업 재개 표시 등 상대 시각도 baseTime에 종속된다. `reset_consumables.sql`도 같은 시각의 스냅샷이므로 다른 날에는 생성기로 다시 만든다.

## 3. 적용 후 확인 및 회신

- `verify.sql`의 actual/expected 77개가 모두 일치하는지 확인한다. [로컬 검증표](reports/verify-results.csv)는 비교 참고이며 실제 개발 DB 적용 결과가 아니다.
- 적용한 브랜치 커밋, 적용 시각, 재생성된 `manifest.json`의 baseTime, verify 결과를 민섭·태완에게 공유한다. 키·로그인 토큰은 공유하지 않는다.
- [계정표](data/accounts.csv) · [주문표](data/orders.csv) · [매장표](data/stores.csv) · [전체 manifest](data/manifest.json)
- 재생성하면 기존 SHA256SUMS는 해당 파일에 대한 체크섬이 아니므로, 커밋된 원본 확인에만 쓴다.

## 4. 서버 코드·설정과의 구분

- 데이터만 넣어도 숨김 메뉴 결함이 고쳐지지는 않는다. [PR #26](https://github.com/CoffeulOrder/Backend/pull/26)은 별도 미머지 수정이다. 현재 develop에서는 R3 숨김 메뉴가 목록에 노출되거나 주문될 수 있다. 이 PR이 반영돼야 S01 고객 목록47개·숨김 메뉴 새 주문404/MN001을 기대할 수 있다.
- 이 브랜치에는 PR #26 Java 코드나 다른 결제·인증 PR 변경을 포함하지 않는다.
- 제공 QA 결제 도구는 `fake` 게이트웨이 전용이다. PortOne 전환 후에는 DEV-1/confirm_fake 도구를 쓰지 않고 실제 테스트 채널 결제 흐름을 별도 확인한다. 원격 게이트웨이는 이 데이터 전달에서 전환하지 않는다.
- 이미지에는 `coffeul.cdn.base-url=https://picsum.photos`가 필요하다. 다른 매장 이미지와 함께 쓰는 서버라면 담당자가 영향 확인 후 QA 환경에서 설정한다.
- 상세 요구사항과 미지원 항목은 [DBA 개정2](../../../docs/qa-seed/DBA_REVISION_2.md)·[범위 대조](../../../docs/qa-seed/COVERAGE.md)를 참고한다. 좌표·재개시각·인기메뉴·목록 필드 확장은 구현 완료가 아니다.

## 5. 검증 범위

이번 전달 시각에 맞춰 생성기 단위검증과 로컬 MySQL QA 통합검증을 재실행했다. 정확한 결과는 [검증 기록](reports/validation.json)에 있다. 실제 원격 DB·Android·PortOne 성공을 뜻하지 않는다. QA 통합 검증은 숨김 메뉴 수정이 있는 로컬 QA 브랜치를 사용하며, 일반 develop과의 차이는 위 PR #26 항목을 따른다.

기존 [검증 문서](../../../docs/qa-seed/VALIDATION.md)의 전체408건은 앞서 완료한 기록이며 이번 재실행 건수와 합산하지 않는다.

이번 재검증: Python18건 통과, SQL actual/expected77개 일치. MySQL QA 최초17건 중16건 통과했고, 기준 시각에서60초가 지나 실패한 재발송 제한1건은 현재 시각으로 상태를 다시 준비해 통과했다. 17건 전체가 한 번에 통과한 실행으로 표현하지 않는다.
