> 2026-11-26 인계 갱신: 동현이 실제 DB 이름을 `coffeul`로 확인했다. 최신 SQL·기준 시각·적용 순서는 [동현 인계 문서](../../qa-data/android/2026-11-26/README.md)를 우선한다. 아래 DB 이름 미확인/검토본 설명은 당시 기록이며, 원격 적용 완료를 뜻하지 않는다.

# Android QA 데이터 사용법 · 2026-11-25

실제 BE 스키마 `develop f603843`, Flyway V1~V4 기준. 기본 동작은 **파일 생성만**이다. 실서버 DB에는 아직 적용하지 않았다. Python 3.9+와 MySQL 8.4 클라이언트를 사용한다.

최신 변경은 [DBA 개정 2](DBA_REVISION_2.md)를 먼저 확인한다. 이 패키지는 격리된 개발/스테이징 전용이며 공개 API 서버에는 적용하지 않는다.

## 1. 처음 준비 · 동현/DB 담당

개발·스테이징 전용 DB의 실제 이름과 접속 권한을 확인한다. 아래 `<ISOLATED_DB_NAME>`은 실제 격리 DB 이름을 전달받아 바꾼다. DB 이름을 추측하지 않는다. 기존 Flyway 마이그레이션이 적용돼 있고 `g.eulji.ac.kr` 학교 도메인 행이 있어야 한다.

```sh
python3 tools/qa-seed/qa_seed.py seed_all --database 'ISOLATED_DB_NAME' --environment dev --output-dir outputs/android-qa-seed
```

생성된 `manifest.json`에서 별칭→숫자 ID, 계정, 주문 사례를 확인한다. 실제 DB PK는 숫자이며 `S01`·`M0101`은 설명용 고정 별칭이다. Android ID 타입은 `Long`을 사용한다. QA PK는 8,510,000,000 이상이다.

담당자가 자신의 MySQL login-path를 준비한 뒤 아래처럼 **직접 적용**한다. 도구는 자격증명 파일을 읽거나 출력하지 않는다.

```sh
python3 tools/qa-seed/qa_seed.py seed_all --database 'ISOLATED_DB_NAME' --environment dev --output-dir outputs/android-qa-seed --execute --confirm-isolated-target --mysql-login-path ormo-qa
```

QA 앱을 닫은 상태에서 적용한다. `mysql --force`는 사용하지 않는다. 동일 ID·자연키의 비QA 데이터와 충돌하면 중단한다. 등록된 QA 소유 데이터만 초기화하며 운영처럼 보이는 DB 이름은 거부한다. 이 가드는 접속 대상의 실제 환경을 보증하는 기능은 아니므로 담당자가 개발 DB임을 확인해야 한다.

## 2. 태완 · 계정과 화면

- 공통 가상 비밀번호: `Qa123456!` / 일반 로그인: `qa01@g.eulji.ac.kr` ~ `qa05@g.eulji.ac.kr`.
- 150건: `qa-heavy@g.eulji.ac.kr`. 20/21건 경계: `qa-page20@…`, `qa-page21@…`.
- 최신 25건 EXPIRED + 과거 5건 COMPLETED: `qa-filtered@…`. ALL/HISTORY API는 먼저 제외한 뒤 5건을 반환한다.
- 진행 1/3/5건: `qa-live1@…`, `qa-live3@…`, `qa-live5@…`. 탈퇴 차단: `qa-wd-block@…`.
- 상태·줄 수·메모: `qa-states@…`. 결제·환불: `qa-payments@…`. 학교 B/C: manifest의 `qa-school-b/c`.
- 탈퇴 시험 `qa-wd01`~`05`, 변경 `qa-pw01`~`05`, 재설정 `qa-reset01`~`05`는 각각 전용 계정이다.
- 가입용 `qa-signup01`~`20@g.eulji.ac.kr`는 **미가입 상태**로 예약한다. 가입 시험과 탈퇴 시험은 계정을 섞지 않는다.
- 사장님: `qa-owner-s01`~`qa-owner-s13` / 배정 직원: `qa-staff-s01`. 비밀번호 동일. 직원 로그인 필드는 `loginId`다.

## 3. 메일을 보내지 않고 인증 시험

```sh
python3 tools/qa-seed/qa_seed.py prepare_verification --email qa-signup01@g.eulji.ac.kr --purpose SIGNUP --scenario valid --database 'ISOLATED_DB_NAME' --environment dev --execute --confirm-isolated-target --mysql-login-path ormo-qa
```

발송 API를 누르지 않고 `POST /api/v1/verifications/email/confirm`에 위 email·purpose와 `code: "123456"`을 보낸다. 실제 확인 API가 발급한 `verificationToken`을 가입/재설정에 사용한다. 5분이 지나면 위 명령을 다시 실행한다. 앱이 반드시 발송 화면을 거쳐야 한다면 담당자가 개발 메일 어댑터 `log`를 사용하고, 발송 후 위 명령으로 해당 QA 주소만 재준비한다. 일반 발송 코드는 계속 난수다.

`--scenario wrong/expired/resend/daily`로 EV004/005/002/003을 준비한다. 상세 경로·자정 제한은 COVERAGE.md를 참고한다.

## 4. 주문 진행·초기화 · 한 줄씩

```sh
python3 tools/qa-seed/qa_api.py confirm_fake --manifest outputs/android-qa-seed/manifest.json --base-url http://127.0.0.1:8080 --order-id <manifest의_숫자_ID> --environment dev --confirm-isolated-target
python3 tools/qa-seed/qa_api.py advance --manifest outputs/android-qa-seed/manifest.json --base-url http://127.0.0.1:8080 --order-id <숫자_ID> --to-status COMPLETED --environment dev --confirm-isolated-target
python3 tools/qa-seed/qa_seed.py advance_order --order-id <숫자_ID> --to-status READY --at 2026-11-25T15:00:00+09:00 --database 'ISOLATED_DB_NAME' --environment dev --execute --confirm-isolated-target --mysql-login-path ormo-qa
python3 tools/qa-seed/qa_seed.py reset_consumables --database 'ISOLATED_DB_NAME' --environment dev --execute --confirm-isolated-target --mysql-login-path ormo-qa
```

API 방식은 실제 상태 이벤트를 발생시킨다. SQL 방식은 타임라인·이력을 함께 채우지만 푸시/이벤트는 발생시키지 않는다. PENDING_PAYMENT는 DEV-1 확인이 먼저다. 역행·취소·거절은 advance 대상이 아니다. 초기화 후에는 앱을 다시 로그인한다. qa-reorder / qa-multistore / qa-longname-ko / qa-longname-en을 추가했으며 R1~R8은 manifest.reorderCases에서 찾는다.

매일 시작 전 첫 `seed_all --execute` 한 줄을 실행하면 된다. 자동 스케줄은 설치하지 않았다. 과거 재현은 `--base-time 2026-11-25T10:00:00+09:00`을 추가한다. 기본 진행 주문 기한은 QA 세션 8시간이며 실제 신규 주문의 20분 정책과 다르다. `--session-hours 1`~`24`로 조절한다.

`verify.sql`은 DB 담당자가 읽기 쿼리로 실행해 실제 건수를 확인한다. seed 파일은 파일 생성 기준 시각을 사용하므로 오래된 SQL을 재사용하지 말고 Python 명령을 다시 실행한다.

**필요한 개발 서버 설정:** `coffeul.payment.gateway=fake`, `coffeul.dev.payment-flow-enabled=true`, 프로필 `dev`, 이미지용 `coffeul.cdn.base-url=https://picsum.photos`. 이 도구는 서버 설정을 변경하지 않는다. 전용 QA 배포에만 적용하고 기존 매장 이미지 경로와 함께 쓰는 환경은 담당자가 먼저 확인한다. 전체 차이·미지원 목록은 COVERAGE.md에 있다.
