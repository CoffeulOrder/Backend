package com.coffeul.auth.infrastructure.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * {@link AuthUserArgumentResolver}를 Spring MVC에 등록한다.
 *
 * <p>커스텀 리졸버는 내장 리졸버 다음, 그리고 애노테이션 없는 객체를 전부 받아버리는
 * ModelAttribute 리졸버보다 앞에서 호출된다. 그래서 컨트롤러가 애노테이션 없이 AuthUser만 적어도
 * 요청 파라미터 바인딩으로 흘러가지 않고 여기로 온다.
 */
@Configuration
public class AuthWebMvcConfig implements WebMvcConfigurer {

    private final AuthUserArgumentResolver authUserArgumentResolver;

    public AuthWebMvcConfig(AuthUserArgumentResolver authUserArgumentResolver) {
        this.authUserArgumentResolver = authUserArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(authUserArgumentResolver);
    }
}
