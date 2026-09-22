package com.coffeul.store.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * schema.sql `merchant` 테이블 매핑. 판매자 정보(상호 · 대표자 · 사업자번호 · 주소 · 연락처)는
 * MS-6(매장 상세)의 통신판매중개자 고지에 쓰인다 — 실제 사업자 정보는 아직 Q3 확인 전이라 값 자체는 가짜다.
 */
@Entity
@Table(name = "merchant")
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_name", nullable = false, length = 100)
    private String businessName;

    @Column(name = "business_reg_no", nullable = false, columnDefinition = "CHAR(10)")
    private String businessRegNo;

    @Column(name = "representative_name", nullable = false, length = 50)
    private String representativeName;

    @Column(name = "business_address", nullable = false)
    private String businessAddress;

    @Column(name = "contact_phone", nullable = false, length = 20)
    private String contactPhone;

    @Column(name = "mail_order_reg_no", length = 50)
    private String mailOrderRegNo;

    @Column(name = "commission_rate", nullable = false)
    private BigDecimal commissionRate;

    protected Merchant() {
    }

    public Long getId() {
        return id;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getBusinessRegNo() {
        return businessRegNo;
    }

    public String getRepresentativeName() {
        return representativeName;
    }

    public String getBusinessAddress() {
        return businessAddress;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public String getMailOrderRegNo() {
        return mailOrderRegNo;
    }

    public BigDecimal getCommissionRate() {
        return commissionRate;
    }
}
