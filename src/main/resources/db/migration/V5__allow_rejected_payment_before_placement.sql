ALTER TABLE orders DROP CHECK ck_orders_placed,
ADD CONSTRAINT ck_orders_placed CHECK (
    status IN ('PENDING_PAYMENT', 'EXPIRED')
    OR (status = 'REJECTED' AND rejected_at IS NOT NULL AND reject_reason_code IS NOT NULL
        AND placed_at IS NULL AND business_date IS NULL AND pickup_no IS NULL)
    OR (placed_at IS NOT NULL AND business_date IS NOT NULL AND pickup_no IS NOT NULL)
);
