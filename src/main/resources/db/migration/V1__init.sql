-- Coffeul 스키마 초안 (Flyway V1__init.sql 후보)
-- 대상: MySQL 8.4 LTS · utf8mb4 · 시간은 UTC로 저장
-- 생성: docs/scope-src/erd.py (직접 수정하지 말고 erd.py를 고친 뒤 다시 생성)
CREATE TABLE `school` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `name` VARCHAR(50) NOT NULL COMMENT '학교명 (예: 을지대학교)',
  `campus` VARCHAR(50) NULL COMMENT '캠퍼스 (예: 성남캠퍼스)',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '운영 상태 — ACTIVE / INACTIVE',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각 (UTC)',
  `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각 (UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_school_name_campus` (`name`, `campus`),
  CONSTRAINT `ck_school_status` CHECK (status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='학교(캠퍼스). 새 학교 추가 = 행 추가, 코드 수정 없음.';

CREATE TABLE `school_email_domain` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `school_id` BIGINT NOT NULL COMMENT '학교',
  `domain` VARCHAR(100) NOT NULL COMMENT '이메일 도메인 (예: g.eulji.ac.kr)',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각 (UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_school_email_domain_domain` (`domain`),
  CONSTRAINT `fk_school_email_domain_school` FOREIGN KEY (`school_id`) REFERENCES `school` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='학교별 허용 이메일 도메인. 가입 시 도메인으로 학교를 결정한다.';

CREATE TABLE `merchant` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `business_name` VARCHAR(100) NOT NULL COMMENT '상호',
  `business_reg_no` CHAR(10) NOT NULL COMMENT '사업자등록번호 (숫자 10자리, 하이픈 없음)',
  `representative_name` VARCHAR(50) NOT NULL COMMENT '대표자명 — 판매자 정보 고지',
  `business_address` VARCHAR(255) NOT NULL COMMENT '사업장 주소 — 판매자 정보 고지',
  `contact_phone` VARCHAR(20) NOT NULL COMMENT '연락처 — 판매자 정보 고지',
  `mail_order_reg_no` VARCHAR(50) NULL COMMENT '통신판매업 신고번호 (Q3 확인 대기)',
  `commission_rate` DECIMAL(5,4) NOT NULL DEFAULT 0.0000 COMMENT '앱 주문 수수료율 (0.0300 = 3%)',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '계약 상태 — ACTIVE / SUSPENDED / TERMINATED',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각 (UTC)',
  `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각 (UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_business_reg_no` (`business_reg_no`),
  CONSTRAINT `ck_merchant_commission_rate` CHECK (commission_rate >= 0 AND commission_rate < 1),
  CONSTRAINT `ck_merchant_status` CHECK (status IN ('ACTIVE','SUSPENDED','TERMINATED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사장님(사업자). PG 가맹 · 수수료 · 판매자 고지의 주체.';

CREATE TABLE `merchant_pg` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `merchant_id` BIGINT NOT NULL COMMENT '사장님',
  `pg_provider` VARCHAR(20) NOT NULL COMMENT 'PG사 코드 (예: TOSS — PG사 미정)',
  `mid` VARCHAR(100) NULL COMMENT 'PG 상점 ID',
  `client_key` VARCHAR(255) NULL COMMENT '결제창용 공개 키 (앱에 내려줘도 되는 키)',
  `secret_ref` VARCHAR(255) NOT NULL COMMENT '비밀키 보관 위치 (Secrets Manager ARN/이름). 평문 금지',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '사용 여부 — ACTIVE / INACTIVE',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각 (UTC)',
  `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각 (UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_pg_merchant_provider` (`merchant_id`, `pg_provider`),
  CONSTRAINT `fk_merchant_pg_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `merchant` (`id`),
  CONSTRAINT `ck_merchant_pg_status` CHECK (status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사장님별 PG 계약 설정. 비밀키 자체는 저장하지 않고 Secrets Manager 경로만.';

CREATE TABLE `store` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `merchant_id` BIGINT NOT NULL COMMENT '사장님',
  `school_id` BIGINT NOT NULL COMMENT '학교',
  `name` VARCHAR(50) NOT NULL COMMENT '매장명 (예: 범석관점)',
  `location` VARCHAR(255) NULL COMMENT '위치 안내',
  `status` VARCHAR(10) NOT NULL DEFAULT 'CLOSED' COMMENT '영업 상태. PAUSED·CLOSED면 주문 생성 차단 — OPEN / PAUSED / CLOSED',
  `notice` VARCHAR(500) NULL COMMENT '매장 공지',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각 (UTC)',
  `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각 (UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_store_school_name` (`school_id`, `name`),
  KEY `idx_store_merchant` (`merchant_id`),
  CONSTRAINT `fk_store_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `merchant` (`id`),
  CONSTRAINT `fk_store_school` FOREIGN KEY (`school_id`) REFERENCES `school` (`id`),
  CONSTRAINT `ck_store_status` CHECK (status IN ('OPEN','PAUSED','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='매장. 사장님 1명이 여러 매장, 매장은 학교 하나에 속한다.';

CREATE TABLE `store_business_hour` (
  `store_id` BIGINT NOT NULL COMMENT '매장 (PK)',
  `day_of_week` TINYINT NOT NULL COMMENT '요일 1=월 … 7=일 (PK)',
  `open_time` TIME NULL COMMENT '여는 시각',
  `close_time` TIME NULL COMMENT '닫는 시각',
  `is_closed` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '휴무일 여부',
  PRIMARY KEY (`store_id`, `day_of_week`),
  CONSTRAINT `fk_store_business_hour_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`),
  CONSTRAINT `ck_store_business_hour_dow` CHECK (day_of_week BETWEEN 1 AND 7),
  CONSTRAINT `ck_store_business_hour_time` CHECK (is_closed OR (open_time IS NOT NULL AND close_time IS NOT NULL AND open_time < close_time))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='요일별 운영 시간(표시용). 주문 가능 여부는 store.status가 기준.';

CREATE TABLE `staff_account` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `merchant_id` BIGINT NULL COMMENT '소속 사장님 (ADMIN은 NULL)',
  `role` VARCHAR(10) NOT NULL COMMENT '역할 — OWNER / STAFF / ADMIN',
  `login_id` VARCHAR(50) NOT NULL COMMENT '로그인 아이디',
  `password_hash` VARCHAR(100) NOT NULL COMMENT 'bcrypt 해시',
  `name` VARCHAR(50) NOT NULL COMMENT '표시 이름',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '계정 상태 — ACTIVE / INACTIVE',
  `failed_login_count` INT NOT NULL DEFAULT 0 COMMENT '연속 로그인 실패 횟수',
  `locked_until` DATETIME(6) NULL COMMENT '다음 로그인 시도 가능 시각 (직원은 잠금 대신 30초 지연)',
  `last_login_at` DATETIME(6) NULL COMMENT '마지막 로그인',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각 (UTC)',
  `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각 (UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_staff_account_login_id` (`login_id`),
  CONSTRAINT `fk_staff_account_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `merchant` (`id`),
  CONSTRAINT `ck_staff_account_role` CHECK (role IN ('OWNER','STAFF','ADMIN')),
  CONSTRAINT `ck_staff_account_status` CHECK (status IN ('ACTIVE','INACTIVE')),
  CONSTRAINT `ck_staff_account_merchant` CHECK ((role = 'ADMIN' AND merchant_id IS NULL) OR (role <> 'ADMIN' AND merchant_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='관리자앱 계정. OWNER=사장님, STAFF=직원(태블릿), ADMIN=운영자(우리 팀).';

CREATE TABLE `staff_store` (
  `staff_account_id` BIGINT NOT NULL COMMENT '직원 계정 (PK)',
  `store_id` BIGINT NOT NULL COMMENT '매장 (PK)',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각 (UTC)',
  PRIMARY KEY (`staff_account_id`, `store_id`),
  KEY `idx_staff_store_store` (`store_id`),
  CONSTRAINT `fk_staff_store_staff` FOREIGN KEY (`staff_account_id`) REFERENCES `staff_account` (`id`),
  CONSTRAINT `fk_staff_store_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='STAFF가 접근할 매장. OWNER는 자기 사장님의 모든 매장에 접근하므로 행이 필요 없다.';

CREATE TABLE `member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `school_id` BIGINT NOT NULL COMMENT '학교 (이메일 도메인으로 결정)',
  `email` VARCHAR(100) NULL COMMENT '학교 이메일. 탈퇴 시 NULL',
  `password_hash` VARCHAR(100) NULL COMMENT 'bcrypt 해시. 탈퇴 시 NULL',
  `name` VARCHAR(30) NOT NULL COMMENT '이름. 탈퇴 시 ''탈퇴회원''',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '회원 상태 — ACTIVE / SUSPENDED / WITHDRAWN',
  `failed_login_count` INT NOT NULL DEFAULT 0 COMMENT '연속 로그인 실패 횟수',
  `locked_until` DATETIME(6) NULL COMMENT '로그인 잠금 해제 시각',
  `last_login_at` DATETIME(6) NULL COMMENT '마지막 로그인',
  `withdrawn_at` DATETIME(6) NULL COMMENT '탈퇴 시각',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각 (UTC)',
  `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각 (UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_email` (`email`),
  CONSTRAINT `fk_member_school` FOREIGN KEY (`school_id`) REFERENCES `school` (`id`),
  CONSTRAINT `ck_member_status` CHECK (status IN ('ACTIVE','SUSPENDED','WITHDRAWN')),
  CONSTRAINT `ck_member_credentials` CHECK (status = 'WITHDRAWN' OR (email IS NOT NULL AND password_hash IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='고객(학생). 탈퇴 시 이메일·비밀번호를 지우고 이름을 비식별화, 주문 기록은 5년 보존.';

CREATE TABLE `terms_agreement` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `member_id` BIGINT NOT NULL COMMENT '회원',
  `terms_type` VARCHAR(20) NOT NULL COMMENT '약관 종류 — SERVICE / PRIVACY',
  `terms_version` VARCHAR(20) NOT NULL COMMENT '약관 버전 (예: 2026-10-01)',
  `agreed_at` DATETIME(6) NOT NULL COMMENT '동의 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_terms_agreement` (`member_id`, `terms_type`, `terms_version`),
  CONSTRAINT `fk_terms_agreement_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `ck_terms_agreement_type` CHECK (terms_type IN ('SERVICE','PRIVACY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='약관 동의 기록. 어떤 버전에 언제 동의했는지가 분쟁 시 증거.';

CREATE TABLE `email_verification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `email` VARCHAR(100) NOT NULL COMMENT '대상 이메일',
  `purpose` VARCHAR(20) NOT NULL COMMENT '용도 — SIGNUP / PASSWORD_RESET',
  `code_hash` CHAR(64) NOT NULL COMMENT '6자리 코드의 SHA-256',
  `expires_at` DATETIME(6) NOT NULL COMMENT '코드 만료 (발송 + 5분)',
  `attempt_count` TINYINT NOT NULL DEFAULT 0 COMMENT '검증 실패 횟수 (5회면 폐기)',
  `verified_at` DATETIME(6) NULL COMMENT '검증 성공 시각',
  `token_hash` CHAR(64) NULL COMMENT '검증 성공 후 발급한 인증 토큰의 SHA-256',
  `token_expires_at` DATETIME(6) NULL COMMENT '인증 토큰 만료 (검증 + 30분)',
  `consumed_at` DATETIME(6) NULL COMMENT '가입·재설정에 사용된 시각 (재사용 방지)',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각 (UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email_verification_token_hash` (`token_hash`),
  KEY `idx_email_verification_email` (`email`, `purpose`, `created_at`),
  CONSTRAINT `ck_email_verification_purpose` CHECK (purpose IN ('SIGNUP','PASSWORD_RESET')),
  CONSTRAINT `ck_email_verification_attempt` CHECK (attempt_count BETWEEN 0 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='이메일 인증코드. 코드와 인증 토큰은 해시로만 저장한다.';

CREATE TABLE `refresh_token` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `subject_type` VARCHAR(10) NOT NULL COMMENT '토큰 주인 종류 — MEMBER / STAFF',
  `subject_id` BIGINT NOT NULL COMMENT 'member.id 또는 staff_account.id',
  `token_hash` CHAR(64) NOT NULL COMMENT '토큰 SHA-256',
  `family_id` CHAR(36) NOT NULL COMMENT '회전 계열 ID (로그인 1회 = 1계열)',
  `expires_at` DATETIME(6) NOT NULL COMMENT '만료 (고객 14일 · 직원 30일)',
  `revoked_at` DATETIME(6) NULL COMMENT '폐기 시각',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각 (UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refresh_token_hash` (`token_hash`),
  KEY `idx_refresh_token_subject` (`subject_type`, `subject_id`),
  KEY `idx_refresh_token_family` (`family_id`),
  CONSTRAINT `ck_refresh_token_subject_type` CHECK (subject_type IN ('MEMBER','STAFF'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='리프레시 토큰(해시). 재발급할 때마다 회전하고, 폐기된 토큰이 다시 오면 같은 계열 전체를 폐기한다.';

CREATE TABLE `category` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `store_id` BIGINT NOT NULL COMMENT '매장',
  `name` VARCHAR(30) NOT NULL COMMENT '카테고리명 (예: ICE COFFEE)',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `is_visible` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '노출 여부',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각 (UTC)',
  `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각 (UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_store_name` (`store_id`, `name`),
  CONSTRAINT `fk_category_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='매장별 카테고리. 메뉴와 다대다.';

CREATE TABLE `menu_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `store_id` BIGINT NOT NULL COMMENT '매장',
  `name` VARCHAR(50) NOT NULL COMMENT '메뉴명 (온도·사이즈 제외한 기본 이름)',
  `description` VARCHAR(255) NULL COMMENT '설명',
  `base_price` INT NOT NULL COMMENT '기본가 (R 사이즈 가격, 원)',
  `image_key` VARCHAR(255) NULL COMMENT 'S3 객체 키 (URL 아님)',
  `is_sold_out` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '품절',
  `status` VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' COMMENT '노출 상태 — ACTIVE / HIDDEN',
  `is_new` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '신메뉴 표시',
  `is_event` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '이벤트 표시',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `version` BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 락 버전',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각 (UTC)',
  `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각 (UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_menu_item_store_name` (`store_id`, `name`),
  KEY `idx_menu_item_store_status` (`store_id`, `status`, `sort_order`),
  CONSTRAINT `fk_menu_item_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`),
  CONSTRAINT `ck_menu_item_price` CHECK (base_price >= 0),
  CONSTRAINT `ck_menu_item_status` CHECK (status IN ('ACTIVE','HIDDEN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='메뉴 1개 = 온도·사이즈를 묶은 기본 메뉴 (예: 아메리카노). 삭제 대신 HIDDEN.';

CREATE TABLE `menu_item_category` (
  `menu_item_id` BIGINT NOT NULL COMMENT '메뉴 (PK)',
  `category_id` BIGINT NOT NULL COMMENT '카테고리 (PK)',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '카테고리 안 정렬',
  PRIMARY KEY (`menu_item_id`, `category_id`),
  KEY `idx_menu_item_category_category` (`category_id`, `sort_order`),
  CONSTRAINT `fk_menu_item_category_menu` FOREIGN KEY (`menu_item_id`) REFERENCES `menu_item` (`id`),
  CONSTRAINT `fk_menu_item_category_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='메뉴 ↔ 카테고리 다대다. 같은 매장끼리만 연결 (애플리케이션에서 검증).';

CREATE TABLE `option_group` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `menu_item_id` BIGINT NOT NULL COMMENT '메뉴',
  `name` VARCHAR(30) NOT NULL COMMENT '그룹명 (예: 온도, 사이즈)',
  `option_type` VARCHAR(20) NOT NULL COMMENT '종류 (EXTRA = 휘핑·얼음 등, 보류) — TEMPERATURE / SIZE / EXTRA',
  `is_required` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '필수 선택',
  `min_select` TINYINT NOT NULL DEFAULT 1 COMMENT '최소 선택 수',
  `max_select` TINYINT NOT NULL DEFAULT 1 COMMENT '최대 선택 수',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각 (UTC)',
  `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각 (UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_option_group_menu_name` (`menu_item_id`, `name`),
  CONSTRAINT `fk_option_group_menu` FOREIGN KEY (`menu_item_id`) REFERENCES `menu_item` (`id`),
  CONSTRAINT `ck_option_group_type` CHECK (option_type IN ('TEMPERATURE','SIZE','EXTRA')),
  CONSTRAINT `ck_option_group_select` CHECK (min_select >= 0 AND max_select >= 1 AND max_select >= min_select)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='메뉴별 옵션 그룹 (온도 · 사이즈). L 추가금이 메뉴마다 달라서 메뉴 소유로 둔다.';

CREATE TABLE `option_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `option_group_id` BIGINT NOT NULL COMMENT '옵션 그룹',
  `name` VARCHAR(30) NOT NULL COMMENT '옵션명 (예: ICE, L)',
  `price_delta` INT NOT NULL DEFAULT 0 COMMENT '추가금 (원). 을지대 L: 메뉴별 500·1000·1500',
  `is_default` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '기본 선택',
  `is_sold_out` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '옵션 품절 (예: L 컵 소진)',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각 (UTC)',
  `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각 (UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_option_item_group_name` (`option_group_id`, `name`),
  CONSTRAINT `fk_option_item_group` FOREIGN KEY (`option_group_id`) REFERENCES `option_group` (`id`),
  CONSTRAINT `ck_option_item_price` CHECK (price_delta >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='옵션 값 (HOT/ICE, R/L)과 추가금.';

CREATE TABLE `orders` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `order_code` VARCHAR(30) NOT NULL COMMENT '고객 표시용 주문번호 (예: CF-20261016-7Q3K9X2M)',
  `member_id` BIGINT NOT NULL COMMENT '주문 고객',
  `store_id` BIGINT NOT NULL COMMENT '매장',
  `school_id` BIGINT NOT NULL COMMENT '학교 (학교 단위 분리 대비)',
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT '주문 상태 — PENDING_PAYMENT / REQUESTED / ACCEPTED / MAKING / READY / COMPLETED / CANCELED / REJECTED / EXPIRED',
  `business_date` DATE NULL COMMENT '영업일 (KST, 결제 확정 시 기록)',
  `pickup_no` SMALLINT UNSIGNED NULL COMMENT '픽업 번호 (매장·영업일별 1부터)',
  `subtotal_amount` INT NOT NULL COMMENT '상품 금액 합계',
  `discount_amount` INT NOT NULL DEFAULT 0 COMMENT '할인 합계 (베타는 0)',
  `total_amount` INT NOT NULL COMMENT '결제 금액 = 상품 합계 − 할인',
  `request_memo` VARCHAR(100) NULL COMMENT '요청사항',
  `reject_reason_code` VARCHAR(30) NULL COMMENT '거절 사유 — SOLD_OUT / INGREDIENT_SHORTAGE / STORE_BUSY / STORE_CLOSING / OTHER',
  `reject_reason_detail` VARCHAR(200) NULL COMMENT '거절 상세 메모',
  `withdrawal_limit_agreed_at` DATETIME(6) NOT NULL COMMENT '청약철회 제한 고지 동의 시각 (결제 직전)',
  `commission_rate_snapshot` DECIMAL(5,4) NOT NULL COMMENT '주문 시점 수수료율 (정산 기준)',
  `idempotency_key` VARCHAR(64) NOT NULL COMMENT '주문 생성 중복 방지 키 (Idempotency-Key 헤더)',
  `request_hash` CHAR(64) NOT NULL COMMENT '주문 요청 본문(매장·항목·옵션·수량·요청사항)의 SHA-256 — 같은 키로 다른 내용이면 OD010',
  `expires_at` DATETIME(6) NOT NULL COMMENT '결제 대기 만료 (생성 + 20분)',
  `placed_at` DATETIME(6) NULL COMMENT '결제 확정 = 매장 접수 시각. 대기 순서 기준',
  `accepted_at` DATETIME(6) NULL COMMENT '수락',
  `making_at` DATETIME(6) NULL COMMENT '제조 시작',
  `ready_at` DATETIME(6) NULL COMMENT '픽업 대기',
  `completed_at` DATETIME(6) NULL COMMENT '픽업 완료',
  `canceled_at` DATETIME(6) NULL COMMENT '고객 취소',
  `rejected_at` DATETIME(6) NULL COMMENT '매장 거절',
  `expired_at` DATETIME(6) NULL COMMENT '결제 대기 만료 처리',
  `version` BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 락 버전',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각 (UTC)',
  `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각 (UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_orders_order_code` (`order_code`),
  UNIQUE KEY `uk_orders_member_idempotency` (`member_id`, `idempotency_key`),
  UNIQUE KEY `uk_orders_pickup_no` (`store_id`, `business_date`, `pickup_no`),
  KEY `idx_orders_store_status_placed` (`store_id`, `status`, `placed_at`),
  KEY `idx_orders_member_created` (`member_id`, `created_at`),
  KEY `idx_orders_status_expires` (`status`, `expires_at`),
  KEY `idx_orders_store_business_date` (`store_id`, `business_date`),
  CONSTRAINT `fk_orders_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_orders_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`),
  CONSTRAINT `fk_orders_school` FOREIGN KEY (`school_id`) REFERENCES `school` (`id`),
  CONSTRAINT `ck_orders_status` CHECK (status IN ('PENDING_PAYMENT','REQUESTED','ACCEPTED','MAKING','READY','COMPLETED','CANCELED','REJECTED','EXPIRED')),
  CONSTRAINT `ck_orders_amounts` CHECK (subtotal_amount >= 0 AND discount_amount >= 0 AND total_amount >= 0 AND total_amount = subtotal_amount - discount_amount),
  CONSTRAINT `ck_orders_reject_reason` CHECK (status <> 'REJECTED' OR reject_reason_code IS NOT NULL),
  CONSTRAINT `ck_orders_reject_reason_code` CHECK (reject_reason_code IS NULL OR reject_reason_code IN ('SOLD_OUT','INGREDIENT_SHORTAGE','STORE_BUSY','STORE_CLOSING','OTHER')),
  CONSTRAINT `ck_orders_placed` CHECK (status IN ('PENDING_PAYMENT','EXPIRED') OR (placed_at IS NOT NULL AND business_date IS NOT NULL AND pickup_no IS NOT NULL)),
  CONSTRAINT `ck_orders_commission` CHECK (commission_rate_snapshot >= 0 AND commission_rate_snapshot < 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='주문. ORDER는 예약어라 복수형. 결제 전(PENDING_PAYMENT)부터 기록하고, 돈 관련 스냅샷을 모두 보관한다.';

CREATE TABLE `order_line` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `order_id` BIGINT NOT NULL COMMENT '주문',
  `menu_item_id` BIGINT NOT NULL COMMENT '메뉴 (참조용)',
  `menu_name_snapshot` VARCHAR(50) NOT NULL COMMENT '주문 시점 메뉴명',
  `base_price_snapshot` INT NOT NULL COMMENT '주문 시점 기본가',
  `unit_price` INT NOT NULL COMMENT '1개 가격 = 기본가 + 옵션 추가금',
  `quantity` SMALLINT NOT NULL COMMENT '수량 (1~20)',
  `line_amount` INT NOT NULL COMMENT '항목 금액 = 1개 가격 × 수량',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각 (UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_order_line_order` (`order_id`),
  CONSTRAINT `fk_order_line_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
  CONSTRAINT `fk_order_line_menu` FOREIGN KEY (`menu_item_id`) REFERENCES `menu_item` (`id`),
  CONSTRAINT `ck_order_line_quantity` CHECK (quantity BETWEEN 1 AND 20),
  CONSTRAINT `ck_order_line_amount` CHECK (base_price_snapshot >= 0 AND unit_price >= base_price_snapshot AND line_amount = unit_price * quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='주문 항목. 메뉴명·가격은 주문 시점 스냅샷 (나중에 메뉴 가격이 바뀌어도 그대로).';

CREATE TABLE `order_line_option` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `order_line_id` BIGINT NOT NULL COMMENT '주문 항목',
  `option_item_id` BIGINT NOT NULL COMMENT '옵션 (참조용)',
  `option_group_name_snapshot` VARCHAR(30) NOT NULL COMMENT '주문 시점 그룹명 (예: 사이즈)',
  `option_item_name_snapshot` VARCHAR(30) NOT NULL COMMENT '주문 시점 옵션명 (예: L)',
  `price_delta_snapshot` INT NOT NULL COMMENT '주문 시점 추가금',
  PRIMARY KEY (`id`),
  KEY `idx_order_line_option_line` (`order_line_id`),
  CONSTRAINT `fk_order_line_option_line` FOREIGN KEY (`order_line_id`) REFERENCES `order_line` (`id`),
  CONSTRAINT `fk_order_line_option_item` FOREIGN KEY (`option_item_id`) REFERENCES `option_item` (`id`),
  CONSTRAINT `ck_order_line_option_price` CHECK (price_delta_snapshot >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='주문 항목에 고른 옵션 스냅샷.';

CREATE TABLE `order_line_discount` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `order_line_id` BIGINT NOT NULL COMMENT '주문 항목',
  `source_type` VARCHAR(20) NOT NULL COMMENT '할인 출처 — PROMOTION / COUPON',
  `source_id` BIGINT NOT NULL COMMENT 'promotion.id 등',
  `name_snapshot` VARCHAR(50) NOT NULL COMMENT '주문 시점 할인명',
  `amount` INT NOT NULL COMMENT '할인 금액',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각 (UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_order_line_discount_line` (`order_line_id`),
  CONSTRAINT `fk_order_line_discount_line` FOREIGN KEY (`order_line_id`) REFERENCES `order_line` (`id`),
  CONSTRAINT `ck_order_line_discount_source` CHECK (source_type IN ('PROMOTION','COUPON')),
  CONSTRAINT `ck_order_line_discount_amount` CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='주문 항목별 할인 내역. 프로모션이 켜지면 사용 (베타는 행 없음).';

CREATE TABLE `order_status_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `order_id` BIGINT NOT NULL COMMENT '주문',
  `from_status` VARCHAR(20) NULL COMMENT '이전 상태 (생성 시 NULL)',
  `to_status` VARCHAR(20) NOT NULL COMMENT '바뀐 상태',
  `actor_type` VARCHAR(10) NOT NULL COMMENT '누가 — CUSTOMER / STAFF / SYSTEM',
  `actor_id` BIGINT NULL COMMENT 'member.id / staff_account.id (SYSTEM이면 NULL)',
  `reason` VARCHAR(200) NULL COMMENT '사유',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각 (UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_order_status_history_order` (`order_id`, `created_at`),
  CONSTRAINT `fk_order_status_history_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
  CONSTRAINT `ck_order_status_history_actor` CHECK (actor_type IN ('CUSTOMER','STAFF','SYSTEM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='상태 변경 이력. ''수락 전에 취소했다'' 같은 분쟁의 유일한 증거.';

CREATE TABLE `order_daily_counter` (
  `store_id` BIGINT NOT NULL COMMENT '매장 (PK)',
  `business_date` DATE NOT NULL COMMENT '영업일 KST (PK)',
  `last_pickup_no` SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '마지막으로 발급한 번호',
  `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각 (UTC)',
  PRIMARY KEY (`store_id`, `business_date`),
  CONSTRAINT `fk_order_daily_counter_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='매장·영업일별 픽업 번호 카운터. 한 문장으로 원자적으로 증가시킨다.';

CREATE TABLE `payment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `order_id` BIGINT NOT NULL COMMENT '주문',
  `store_id` BIGINT NOT NULL COMMENT '매장',
  `merchant_pg_id` BIGINT NOT NULL COMMENT '사용한 PG 설정',
  `pg_provider` VARCHAR(20) NOT NULL COMMENT 'PG사 코드',
  `attempt_no` SMALLINT NOT NULL COMMENT '결제 시도 번호 (주문마다 1부터, 최대 5)',
  `pg_order_id` VARCHAR(64) NOT NULL COMMENT 'PG에 보낸 주문 ID = {order_code}-{attempt_no} (영문·숫자·-·_ 6~64자)',
  `payment_key` VARCHAR(200) NULL COMMENT 'PG 결제 키 (승인 요청 시 받음)',
  `method` VARCHAR(30) NULL COMMENT '결제 수단 (PG 응답 그대로)',
  `amount` INT NOT NULL COMMENT '결제 금액',
  `status` VARCHAR(20) NOT NULL COMMENT '결제 상태 (READY=결제창 진행 중, UNKNOWN=PG 응답 끊김) — READY / APPROVED / FAILED / UNKNOWN / CANCELED',
  `approved_at` DATETIME(6) NULL COMMENT '승인 시각',
  `failed_at` DATETIME(6) NULL COMMENT '실패 시각',
  `canceled_at` DATETIME(6) NULL COMMENT '전액 취소 완료 시각',
  `failure_code` VARCHAR(100) NULL COMMENT 'PG 실패 코드 · 내부 사유 (SUPERSEDED=새 시도로 대체, ORDER_EXPIRED)',
  `failure_message` VARCHAR(255) NULL COMMENT 'PG 실패 메시지',
  `confirm_idempotency_key` VARCHAR(64) NOT NULL COMMENT '승인 API 멱등 키 (Idempotency-Key 헤더)',
  `approved_order_id` BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('APPROVED','CANCELED') THEN order_id END) STORED NULL COMMENT '승인된 결제만 order_id를 담는 생성 컬럼 → 주문당 승인 1건',
  `active_order_id` BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('READY','UNKNOWN') THEN order_id END) STORED NULL COMMENT '진행 중(READY·UNKNOWN) 시도만 order_id를 담는 생성 컬럼 → 주문당 진행 중 시도 1개 (결과 모르는 동안 새 결제 금지)',
  `version` BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 락 버전',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각 (UTC)',
  `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각 (UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_pg_order_id` (`pg_order_id`),
  UNIQUE KEY `uk_payment_payment_key` (`payment_key`),
  UNIQUE KEY `uk_payment_confirm_idempotency` (`confirm_idempotency_key`),
  UNIQUE KEY `uk_payment_approved_order` (`approved_order_id`),
  UNIQUE KEY `uk_payment_active_order` (`active_order_id`),
  UNIQUE KEY `uk_payment_order_attempt` (`order_id`, `attempt_no`),
  KEY `idx_payment_order` (`order_id`),
  KEY `idx_payment_status_updated` (`status`, `updated_at`),
  CONSTRAINT `fk_payment_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
  CONSTRAINT `fk_payment_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`),
  CONSTRAINT `fk_payment_merchant_pg` FOREIGN KEY (`merchant_pg_id`) REFERENCES `merchant_pg` (`id`),
  CONSTRAINT `ck_payment_amount` CHECK (amount > 0),
  CONSTRAINT `ck_payment_attempt_no` CHECK (attempt_no BETWEEN 1 AND 5),
  CONSTRAINT `ck_payment_status` CHECK (status IN ('READY','APPROVED','FAILED','UNKNOWN','CANCELED')),
  CONSTRAINT `ck_payment_approved` CHECK (status NOT IN ('APPROVED','CANCELED') OR (payment_key IS NOT NULL AND approved_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='결제 시도 1건 = 1행. 주문당 승인 1건 · 진행 중 시도 1건을 생성 컬럼 + UNIQUE로 DB가 보장. 카드 정보는 저장하지 않는다.';

CREATE TABLE `payment_cancel` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `payment_id` BIGINT NOT NULL COMMENT '결제',
  `cancel_amount` INT NOT NULL COMMENT '취소 금액 (베타는 전액만)',
  `cancel_reason` VARCHAR(200) NOT NULL COMMENT 'PG에 보내는 취소 사유',
  `requested_by_type` VARCHAR(10) NOT NULL COMMENT '요청 주체 — CUSTOMER / STAFF / SYSTEM',
  `status` VARCHAR(20) NOT NULL COMMENT '처리 상태 — REQUESTED / SUCCEEDED / FAILED',
  `idempotency_key` VARCHAR(64) NOT NULL COMMENT '취소 API 멱등 키',
  `pg_transaction_key` VARCHAR(200) NULL COMMENT 'PG 취소 거래 키',
  `attempt_count` INT NOT NULL DEFAULT 0 COMMENT '시도 횟수',
  `next_retry_at` DATETIME(6) NULL COMMENT '다음 재시도 시각',
  `last_error_code` VARCHAR(100) NULL COMMENT '마지막 실패 코드',
  `last_error_message` VARCHAR(255) NULL COMMENT '마지막 실패 메시지',
  `succeeded_at` DATETIME(6) NULL COMMENT '취소 완료 시각',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각 (UTC)',
  `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각 (UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_cancel_idempotency` (`idempotency_key`),
  KEY `idx_payment_cancel_payment` (`payment_id`),
  KEY `idx_payment_cancel_retry` (`status`, `next_retry_at`),
  CONSTRAINT `fk_payment_cancel_payment` FOREIGN KEY (`payment_id`) REFERENCES `payment` (`id`),
  CONSTRAINT `ck_payment_cancel_amount` CHECK (cancel_amount > 0),
  CONSTRAINT `ck_payment_cancel_requested_by` CHECK (requested_by_type IN ('CUSTOMER','STAFF','SYSTEM')),
  CONSTRAINT `ck_payment_cancel_status` CHECK (status IN ('REQUESTED','SUCCEEDED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='환불(결제 취소) 요청과 재시도 상태. 실패해도 지워지지 않고 다시 시도된다.';

CREATE TABLE `device_token` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `owner_type` VARCHAR(10) NOT NULL COMMENT '토큰 주인 종류 — MEMBER / STAFF',
  `owner_id` BIGINT NOT NULL COMMENT 'member.id 또는 staff_account.id',
  `app_type` VARCHAR(10) NOT NULL COMMENT '앱 종류 — CUSTOMER / MANAGER',
  `store_id` BIGINT NULL COMMENT '관리자앱이 선택한 매장 (고객앱은 NULL)',
  `expo_push_token` VARCHAR(255) NOT NULL COMMENT 'ExponentPushToken[...]',
  `platform` VARCHAR(10) NOT NULL COMMENT '플랫폼 — IOS / ANDROID',
  `is_active` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '사용 중',
  `last_registered_at` DATETIME(6) NOT NULL COMMENT '마지막 등록·갱신',
  `deactivated_at` DATETIME(6) NULL COMMENT '비활성 시각 (로그아웃·DeviceNotRegistered)',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각 (UTC)',
  `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각 (UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_token_token` (`expo_push_token`),
  KEY `idx_device_token_owner` (`owner_type`, `owner_id`, `is_active`),
  KEY `idx_device_token_store` (`store_id`, `is_active`),
  CONSTRAINT `fk_device_token_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`),
  CONSTRAINT `ck_device_token_owner_type` CHECK (owner_type IN ('MEMBER','STAFF')),
  CONSTRAINT `ck_device_token_app_type` CHECK (app_type IN ('CUSTOMER','MANAGER')),
  CONSTRAINT `ck_device_token_platform` CHECK (platform IN ('IOS','ANDROID')),
  CONSTRAINT `ck_device_token_app_owner` CHECK ((app_type = 'CUSTOMER' AND owner_type = 'MEMBER' AND store_id IS NULL) OR (app_type = 'MANAGER' AND owner_type = 'STAFF' AND store_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Expo 푸시 토큰. 관리자앱 토큰은 현재 선택한 매장을 함께 저장해 새 주문 알림 대상이 된다.';

CREATE TABLE `notification_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `device_token_id` BIGINT NULL COMMENT '대상 토큰',
  `order_id` BIGINT NULL COMMENT '관련 주문',
  `notification_type` VARCHAR(30) NOT NULL COMMENT '알림 종류 — NEW_ORDER / ORDER_READY / ORDER_REJECTED / REFUND_COMPLETED',
  `title` VARCHAR(100) NOT NULL COMMENT '제목',
  `body` VARCHAR(255) NOT NULL COMMENT '본문',
  `status` VARCHAR(20) NOT NULL COMMENT '발송 상태 — QUEUED / SENT / FAILED / DELIVERY_FAILED',
  `expo_ticket_id` VARCHAR(100) NULL COMMENT 'Expo 티켓 ID',
  `error_code` VARCHAR(50) NULL COMMENT '실패 코드 (예: DeviceNotRegistered)',
  `sent_at` DATETIME(6) NULL COMMENT '발송 시각',
  `receipt_checked_at` DATETIME(6) NULL COMMENT '영수증 확인 시각 (발송 15분 후)',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각 (UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_notification_log_receipt` (`status`, `receipt_checked_at`, `sent_at`),
  KEY `idx_notification_log_order` (`order_id`),
  CONSTRAINT `fk_notification_log_device` FOREIGN KEY (`device_token_id`) REFERENCES `device_token` (`id`),
  CONSTRAINT `fk_notification_log_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
  CONSTRAINT `ck_notification_log_type` CHECK (notification_type IN ('NEW_ORDER','ORDER_READY','ORDER_REJECTED','REFUND_COMPLETED')),
  CONSTRAINT `ck_notification_log_status` CHECK (status IN ('QUEUED','SENT','FAILED','DELIVERY_FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='푸시 발송 기록과 Expo 영수증 확인 결과.';

CREATE TABLE `promotion` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `merchant_id` BIGINT NOT NULL COMMENT '사장님',
  `store_id` BIGINT NULL COMMENT '특정 매장 (NULL = 사장님 전 매장)',
  `name` VARCHAR(50) NOT NULL COMMENT '프로모션명',
  `discount_type` VARCHAR(10) NOT NULL COMMENT '할인 방식 — RATE / AMOUNT',
  `discount_value` INT NOT NULL COMMENT 'RATE면 %, AMOUNT면 원',
  `starts_at` DATETIME(6) NOT NULL COMMENT '시작',
  `ends_at` DATETIME(6) NOT NULL COMMENT '종료',
  `is_active` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '사용 여부',
  `priority` INT NOT NULL DEFAULT 0 COMMENT '여러 개 겹칠 때 우선순위',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각 (UTC)',
  `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각 (UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_promotion_active` (`merchant_id`, `is_active`, `starts_at`, `ends_at`),
  CONSTRAINT `fk_promotion_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `merchant` (`id`),
  CONSTRAINT `fk_promotion_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`),
  CONSTRAINT `ck_promotion_type` CHECK (discount_type IN ('RATE','AMOUNT')),
  CONSTRAINT `ck_promotion_value` CHECK (discount_value > 0 AND (discount_type <> 'RATE' OR discount_value <= 100)),
  CONSTRAINT `ck_promotion_period` CHECK (starts_at < ends_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='기간 할인 (예: 시험기간). 구조만 먼저 만든다.';

CREATE TABLE `promotion_target` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `promotion_id` BIGINT NOT NULL COMMENT '프로모션',
  `category_id` BIGINT NULL COMMENT '대상 카테고리',
  `menu_item_id` BIGINT NULL COMMENT '대상 메뉴',
  PRIMARY KEY (`id`),
  KEY `idx_promotion_target_promotion` (`promotion_id`),
  CONSTRAINT `fk_promotion_target_promotion` FOREIGN KEY (`promotion_id`) REFERENCES `promotion` (`id`),
  CONSTRAINT `fk_promotion_target_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`),
  CONSTRAINT `fk_promotion_target_menu` FOREIGN KEY (`menu_item_id`) REFERENCES `menu_item` (`id`),
  CONSTRAINT `ck_promotion_target_one` CHECK ((category_id IS NULL) <> (menu_item_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='프로모션 대상 (카테고리 또는 메뉴 중 하나).';

CREATE TABLE `settlement` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `merchant_id` BIGINT NOT NULL COMMENT '사장님',
  `period_start` DATE NOT NULL COMMENT '정산 시작일',
  `period_end` DATE NOT NULL COMMENT '정산 종료일',
  `order_count` INT NOT NULL DEFAULT 0 COMMENT '완료 주문 수',
  `gross_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '결제 금액 합계',
  `refund_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '환불 합계',
  `net_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '순매출 = 결제 − 환불',
  `commission_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '수수료 합계 (기준은 사장님 합의 대기)',
  `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '정산 상태 — DRAFT / CONFIRMED / INVOICED / PAID',
  `confirmed_at` DATETIME(6) NULL COMMENT '확정 시각',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각 (UTC)',
  `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각 (UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_merchant_period` (`merchant_id`, `period_start`),
  CONSTRAINT `fk_settlement_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `merchant` (`id`),
  CONSTRAINT `ck_settlement_period` CHECK (period_start <= period_end),
  CONSTRAINT `ck_settlement_amounts` CHECK (gross_amount >= 0 AND refund_amount >= 0 AND net_amount = gross_amount - refund_amount AND commission_amount >= 0),
  CONSTRAINT `ck_settlement_status` CHECK (status IN ('DRAFT','CONFIRMED','INVOICED','PAID'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사장님별 월 수수료 정산. 주문의 수수료율 스냅샷으로 계산한다.';
