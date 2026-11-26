> 2026-11-26 인계 갱신: 동현이 실제 DB 이름을 `coffeul`로 확인했다. 최신 SQL·기준 시각·적용 순서는 [동현 인계 문서](../../qa-data/android/2026-11-26/README.md)를 우선한다. 아래 DB 이름 미확인/검토본 설명은 당시 기록이며, 원격 적용 완료를 뜻하지 않는다.

# Android QA 시드

[한 장 사용법](../../docs/qa-seed/QUICKSTART.md) · [요구사항 대조와 Q1~Q10](../../docs/qa-seed/COVERAGE.md)

- Python 표준 라이브러리만 사용. 실제 비밀 파일·운영 설정을 읽지 않는다.
- 기본은 SQL/manifest/CSV/FE mock 생성만. --execute는 담당자의 명시적 실행이며 mysql login-path를 클라이언트가 사용한다.
- 시드 자체는 운영 Java/스키마 마이그레이션을 바꾸지 않는다. R3에서 발견한 숨김 메뉴 결함의 최소 수정은 별도 PR로 준비했다. QA 보조 registry 테이블만 추가한다.
- 테스트: `python3 -m unittest discover -s tools/qa-seed -p 'test_*.py'`
- MySQL/API 검증: `./mvnw -Dtest=AndroidQaSeedIntegrationTest test` (Java 21, Docker 필요)

원격 적용/배포/머지는 수행하지 않았다. 신규·이벤트 배지의 모든 조합과 미지원 상태 구분은 COVERAGE.md를 참고한다.

개정2는 [DBA 추가 요청 반영표](../../docs/qa-seed/DBA_REVISION_2.md)를 우선한다. 공개 서버 적용 금지. 실제 DB명 미확인 검토본은 --execute에서 차단한다.
