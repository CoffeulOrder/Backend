package com.coffeul.store.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * schema.sql `merchant` 테이블 매핑 (일부 컬럼만).
 * 사업자등록번호 등 실제 값이 없어서 아직 seed 데이터는 없다 — 매핑만 미리 해 둠.
 */
@Entity
@Table(name = "merchant")
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "commission_rate", nullable = false)
    private BigDecimal commissionRate;

    protected Merchant() {
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getCommissionRate() {
        return commissionRate;
    }
}
