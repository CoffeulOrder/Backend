package com.coffeul.auth.infrastructure.security;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.infrastructure.JwtAccessTokenService;
import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 컨트롤러 인자 {@link AuthUser}를 Authorization 헤더의 access 토큰에서 만들어 넣는다.
 *
 * <p>rules.py AUTH_SPEC: "컨트롤러는 auth.api.AuthUser(id, type, role, schoolId, merchantId)만 받는다 —
 * 토큰 파싱 코드를 각 모듈에 두지 않는다." 그래서 파싱은 이 클래스 하나에만 있고, member · order · store의
 * 컨트롤러는 인자에 AuthUser를 적기만 한다.
 *
 * <p>애노테이션(@LoginUser 같은 것)을 두지 않고 타입으로만 판별한다 — 스펙이 "AuthUser만 받는다"고
 * 정했으므로 다른 모듈이 추가로 알아야 할 것을 만들지 않는다.
 *
 * <p>헤더가 없거나 Bearer 형식이 아니거나 토큰이 무효 · 만료면 C002(401). 계정 상태(탈퇴 · 정지) 확인은
 * 여기서 하지 않는다 — REQ-AUTH-012는 주문 생성 · 결제 승인 · 매장용 API가 각자 DB에서 확인하도록 정했고,
 * auth가 그걸 하려면 다른 모듈을 import해야 해서 모듈 경계(REQ-AUTH-011)가 깨진다.
 *
 * <p>MS-27과 함께 들어올 JwtFilter · SecurityConfig가 앞단에서 토큰을 먼저 검증하게 되어도 이 리졸버는
 * 그대로 쓸 수 있다. 그때는 여기서 다시 파싱하지 말고 필터가 넣어둔 값을 꺼내도록 바꾸면 된다.
 */
@Component
public class AuthUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtAccessTokenService jwtAccessTokenService;

    public AuthUserArgumentResolver(JwtAccessTokenService jwtAccessTokenService) {
        this.jwtAccessTokenService = jwtAccessTokenService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return AuthUser.class.equals(parameter.getParameterType());
    }

    @Override
    public AuthUser resolveArgument(MethodParameter parameter,
                                     ModelAndViewContainer mavContainer,
                                     NativeWebRequest webRequest,
                                     WebDataBinderFactory binderFactory) {
        return jwtAccessTokenService.decode(bearerToken(webRequest.getHeader(HttpHeaders.AUTHORIZATION)));
    }

    /**
     * {@code Authorization: Bearer <token>}에서 토큰만 꺼낸다. 형식이 아니면 C002(401).
     * RFC 6750은 스킴 이름을 대소문자 구분 없이 보라고 해서 {@code bearer}도 받는다.
     */
    static String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null
                || !authorizationHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return token;
    }
}
