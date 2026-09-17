package com.coffeul;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * 모듈 경계 검증 (rules.py DOD: "모듈 경계 검증(ApplicationModules.verify) 포함 전체 테스트 통과").
 * 다른 모듈의 내부 패키지를 직접 참조하면 이 테스트가 실패한다 — 반드시 상대 모듈의 api 패키지만 통해야 한다.
 */
class ModularityTests {

    static final ApplicationModules MODULES = ApplicationModules.of(CoffeulApplication.class);

    @Test
    void verifiesModuleStructure() {
        MODULES.verify();
    }

    @Test
    void printsModuleStructure() {
        System.out.println(MODULES);
    }
}
