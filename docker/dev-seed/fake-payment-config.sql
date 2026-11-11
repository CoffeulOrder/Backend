-- 로컬 개발용 가짜 사장님에게만 FAKE 결제 설정을 넣는다. 실제 PG 키나 계약 정보가 아니다.
INSERT INTO merchant_pg (merchant_id, pg_provider, client_key, secret_ref, status)
SELECT id, 'FAKE', 'fake_client_key_local_only', 'fake://local-only', 'ACTIVE'
FROM merchant WHERE business_reg_no = '9999999999'
ON DUPLICATE KEY UPDATE id = merchant_pg.id;
