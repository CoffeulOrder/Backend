-- 로컬 개발 전용 가짜 데이터. docker-compose.yml의 dev-seed 서비스만 실행한다 (Flyway 마이그레이션이 아님).
-- 상호·사업자번호·주소·연락처는 전부 가짜이고, 진짜 사업자 정보(Q3)가 나오면 그건 운영 DB에 따로 넣는다.
-- 아래 세 계정의 비밀번호는 모두 'dev-password-1234' (bcrypt 해시). 운영 DB에는 절대 넣지 않는다.
--   고객   dev-member@g.eulji.ac.kr   (POST /api/v1/auth/login)
--   사장님 dev-owner                  (POST /api/v1/auth/staff/login) — 두 매장 전체 접근
--   직원   dev-staff                  (POST /api/v1/auth/staff/login) — 범석관점만 배정

START TRANSACTION;

INSERT INTO `merchant` (`business_name`, `business_reg_no`, `representative_name`, `business_address`, `contact_phone`, `commission_rate`, `status`)
VALUES ('[DEV] 개발용 카페', '9999999999', '개발용', '[DEV] 개발용 주소', '010-0000-0000', 0.0500, 'ACTIVE');
SET @merchant_id = LAST_INSERT_ID();

SET @school_id = (SELECT `id` FROM `school` WHERE `name` = '을지대학교' AND `campus` = '성남캠퍼스');

INSERT INTO `store` (`merchant_id`, `school_id`, `name`, `location`, `status`)
VALUES (@merchant_id, @school_id, '범석관점', '[DEV] 범석관 1층', 'OPEN');
SET @store_1 = LAST_INSERT_ID();
INSERT INTO `store` (`merchant_id`, `school_id`, `name`, `location`, `status`)
VALUES (@merchant_id, @school_id, '뉴밀레니엄관점', '[DEV] 뉴밀레니엄관 1층', 'OPEN');
SET @store_2 = LAST_INSERT_ID();

-- 운영 시간(표시용): 평일 08:30~18:00, 주말 휴무
INSERT INTO `store_business_hour` (`store_id`, `day_of_week`, `open_time`, `close_time`, `is_closed`) VALUES
  (@store_1, 1, '08:30', '18:00', FALSE), (@store_1, 2, '08:30', '18:00', FALSE), (@store_1, 3, '08:30', '18:00', FALSE),
  (@store_1, 4, '08:30', '18:00', FALSE), (@store_1, 5, '08:30', '18:00', FALSE),
  (@store_1, 6, NULL, NULL, TRUE), (@store_1, 7, NULL, NULL, TRUE),
  (@store_2, 1, '08:30', '18:00', FALSE), (@store_2, 2, '08:30', '18:00', FALSE), (@store_2, 3, '08:30', '18:00', FALSE),
  (@store_2, 4, '08:30', '18:00', FALSE), (@store_2, 5, '08:30', '18:00', FALSE),
  (@store_2, 6, NULL, NULL, TRUE), (@store_2, 7, NULL, NULL, TRUE);

INSERT INTO `staff_account` (`merchant_id`, `role`, `login_id`, `password_hash`, `name`) VALUES
  (@merchant_id, 'OWNER', 'dev-owner', '$2y$10$a8er2ny8gb/WqGa/NTBYg.UYpBN3KsuD2bjcW7YgcezQmfSe9EmJi', '[DEV] 사장님');
INSERT INTO `staff_account` (`merchant_id`, `role`, `login_id`, `password_hash`, `name`) VALUES
  (@merchant_id, 'STAFF', 'dev-staff', '$2y$10$a8er2ny8gb/WqGa/NTBYg.UYpBN3KsuD2bjcW7YgcezQmfSe9EmJi', '[DEV] 직원');
SET @staff_id = LAST_INSERT_ID();
INSERT INTO `staff_store` (`staff_account_id`, `store_id`) VALUES (@staff_id, @store_1);

INSERT INTO `member` (`school_id`, `email`, `password_hash`, `name`)
VALUES (@school_id, 'dev-member@g.eulji.ac.kr', '$2y$10$a8er2ny8gb/WqGa/NTBYg.UYpBN3KsuD2bjcW7YgcezQmfSe9EmJi', '[DEV] 회원');

COMMIT;
