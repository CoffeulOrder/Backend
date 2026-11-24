CREATE TABLE payment_reconciliation (
    payment_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME(6) NOT NULL,
    last_error_code VARCHAR(100) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (payment_id),
    KEY idx_payment_reconciliation_due (status, next_retry_at),
    CONSTRAINT fk_payment_reconciliation_payment FOREIGN KEY (payment_id) REFERENCES payment (id),
    CONSTRAINT ck_payment_reconciliation_status CHECK (status IN ('WAITING', 'COMPLETE', 'FAILED')),
    CONSTRAINT ck_payment_reconciliation_attempt CHECK (attempt_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='만료 주문의 PG 결제 재조회. 미결제와 일시 오류는 재시도하고 식별 정보 불일치는 확인 대기로 남긴다.';
