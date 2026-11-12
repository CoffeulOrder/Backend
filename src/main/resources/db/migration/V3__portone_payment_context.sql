ALTER TABLE merchant_pg
    ADD COLUMN portone_store_id VARCHAR(100) NULL COMMENT 'PortOne 상점 ID';

ALTER TABLE payment
    ADD COLUMN portone_store_id VARCHAR(100) NULL COMMENT '결제 시도 당시 PortOne 상점 ID',
    ADD COLUMN portone_channel_key VARCHAR(255) NULL COMMENT '결제 시도 당시 PortOne 결제창 채널 키';
