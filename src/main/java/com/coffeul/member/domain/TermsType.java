package com.coffeul.member.domain;

import java.util.Optional;

/** 약관 종류 (REQ-U-003). 둘 다 필수 동의 — 선택 약관은 아직 없다. */
public enum TermsType {

    SERVICE,
    PRIVACY;

    public static Optional<TermsType> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        for (TermsType type : values()) {
            if (type.name().equals(raw)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
