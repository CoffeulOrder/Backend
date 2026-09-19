package com.coffeul.auth;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.auth.infrastructure.security.AuthUserArgumentResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.ServletModelAttributeMethodProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 리졸버가 실제 애플리케이션 컨텍스트의 Spring MVC에 등록됐는지, 그리고 올바른 순서에 있는지 본다.
 * 헤더 파싱 · 클레임 복원 자체는 {@link AuthUserArgumentResolverTest}(단위)가 본다.
 *
 * <p>테스트 전용 컨트롤러를 띄우지 않는다 — 테스트 소스도 {@code com.coffeul} 아래라 {@code @RestController}를
 * 붙이면 컴포넌트 스캔에 걸려서 모든 통합 테스트 컨텍스트에 그 엔드포인트가 딸려 붙고 springdoc 문서에도 나온다.
 * ({@code @RequestMapping}만 붙이는 우회는 핸들러로 등록되지 않아 404가 된다.) 대신 등록 결과를 직접 본다.
 */
class AuthUserResolverWiringTest extends AbstractIntegrationTest {

    @Autowired
    private RequestMappingHandlerAdapter requestMappingHandlerAdapter;

    @Test
    void 리졸버가_MVC_인자_리졸버_목록에_등록된다() {
        assertThat(resolvers()).anyMatch(AuthUserArgumentResolver.class::isInstance);
    }

    @Test
    void 애노테이션_없는_객체를_받는_기본_리졸버보다_먼저_호출된다() {
        // 마지막 ServletModelAttributeMethodProcessor가 애노테이션 없는 객체를 전부 받아버리는 폴백이다.
        // 우리 리졸버가 그보다 뒤에 있으면 컨트롤러의 AuthUser 인자가 요청 파라미터 바인딩으로 흘러가
        // 401 대신 500이 난다. 그래서 순서까지 못 박아 둔다.
        List<HandlerMethodArgumentResolver> resolvers = resolvers();

        int ours = -1;
        int modelAttributeFallback = -1;
        for (int i = 0; i < resolvers.size(); i++) {
            HandlerMethodArgumentResolver resolver = resolvers.get(i);
            if (resolver instanceof AuthUserArgumentResolver) {
                ours = i;
            } else if (resolver instanceof ServletModelAttributeMethodProcessor) {
                modelAttributeFallback = i;
            }
        }

        assertThat(ours).as("AuthUserArgumentResolver가 등록돼 있어야 한다").isNotNegative();
        assertThat(modelAttributeFallback).as("ModelAttribute 폴백 리졸버를 찾아야 한다").isNotNegative();
        assertThat(ours).isLessThan(modelAttributeFallback);
    }

    private List<HandlerMethodArgumentResolver> resolvers() {
        List<HandlerMethodArgumentResolver> resolvers = requestMappingHandlerAdapter.getArgumentResolvers();
        assertThat(resolvers).isNotNull();
        return resolvers;
    }
}
