package com.coffeul.auth;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.infrastructure.JwtAccessTokenService;
import com.coffeul.auth.infrastructure.security.AuthUserArgumentResolver;
import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AuthUser 인자 리졸버 — 헤더 파싱과 클레임 복원. DB · Spring 없이 도는 단위 테스트.
 * MVC에 실제로 등록됐는지는 {@link AuthUserResolverIntegrationTest}가 본다.
 */
class AuthUserArgumentResolverTest {

    private static final String SECRET = "coffeul-test-secret-key-at-least-32-bytes-long-for-hs256";

    private final JwtAccessTokenService jwt = new JwtAccessTokenService(SECRET, 60);
    private final AuthUserArgumentResolver resolver = new AuthUserArgumentResolver(jwt);

    @Test
    void AuthUser_타입_인자만_지원한다() {
        assertThat(resolver.supportsParameter(parameterAt(0))).isTrue();
        assertThat(resolver.supportsParameter(parameterAt(1))).isFalse();
    }

    @Test
    void 고객_토큰은_학교_id까지_복원한다() {
        AuthUser member = new AuthUser(42L, AuthUser.SubjectType.MEMBER, AuthUser.Role.CUSTOMER, 7L, null);

        AuthUser resolved = resolve("Bearer " + jwt.encode(member, Instant.now()));

        assertThat(resolved.id()).isEqualTo(42L);
        assertThat(resolved.type()).isEqualTo(AuthUser.SubjectType.MEMBER);
        assertThat(resolved.role()).isEqualTo(AuthUser.Role.CUSTOMER);
        assertThat(resolved.schoolId()).isEqualTo(7L);
        assertThat(resolved.merchantId()).isNull();
    }

    @Test
    void 직원_토큰은_사장님_id까지_복원한다() {
        AuthUser staff = new AuthUser(9L, AuthUser.SubjectType.STAFF, AuthUser.Role.OWNER, null, 3L);

        AuthUser resolved = resolve("Bearer " + jwt.encode(staff, Instant.now()));

        assertThat(resolved.id()).isEqualTo(9L);
        assertThat(resolved.type()).isEqualTo(AuthUser.SubjectType.STAFF);
        assertThat(resolved.role()).isEqualTo(AuthUser.Role.OWNER);
        assertThat(resolved.merchantId()).isEqualTo(3L);
        assertThat(resolved.schoolId()).isNull();
    }

    @Test
    void 스킴은_대소문자를_구분하지_않는다() {
        // RFC 6750: 스킴 이름은 대소문자 구분 없이 본다. 클라이언트가 bearer로 보내도 401이 되면 안 된다.
        AuthUser member = new AuthUser(1L, AuthUser.SubjectType.MEMBER, AuthUser.Role.CUSTOMER, 1L, null);
        String token = jwt.encode(member, Instant.now());

        assertThat(resolve("bearer " + token).id()).isEqualTo(1L);
        assertThat(resolve("BEARER " + token).id()).isEqualTo(1L);
    }

    @Test
    void 헤더가_없으면_401이다() {
        assertUnauthorized(null);
    }

    @Test
    void Bearer가_아닌_스킴은_401이다() {
        assertUnauthorized("Basic dXNlcjpwYXNz");
    }

    @Test
    void 토큰_자리가_비어_있으면_401이다() {
        assertUnauthorized("Bearer ");
        assertUnauthorized("Bearer    ");
    }

    @Test
    void 서명이_다른_토큰은_401이다() {
        AuthUser member = new AuthUser(1L, AuthUser.SubjectType.MEMBER, AuthUser.Role.CUSTOMER, 1L, null);
        String foreign = new JwtAccessTokenService("another-secret-key-at-least-32-bytes-long-for-hs256!", 60)
                .encode(member, Instant.now());

        assertUnauthorized("Bearer " + foreign);
    }

    @Test
    void 만료된_토큰은_401이다() {
        AuthUser member = new AuthUser(1L, AuthUser.SubjectType.MEMBER, AuthUser.Role.CUSTOMER, 1L, null);
        String expired = jwt.encode(member, Instant.now().minus(Duration.ofHours(2)));

        assertUnauthorized("Bearer " + expired);
    }

    private AuthUser resolve(String authorizationHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorizationHeader != null) {
            request.addHeader("Authorization", authorizationHeader);
        }
        return resolver.resolveArgument(parameterAt(0), null, new ServletWebRequest(request), null);
    }

    private void assertUnauthorized(String authorizationHeader) {
        assertThatThrownBy(() -> resolve(authorizationHeader))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
    }

    private MethodParameter parameterAt(int index) {
        try {
            Method handler = ReflectionTarget.class.getDeclaredMethod("handler", AuthUser.class, String.class);
            return new MethodParameter(handler, index);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    /** 리졸버가 볼 인자 목록을 만들기 위한 더미. */
    private static final class ReflectionTarget {
        @SuppressWarnings("unused")
        void handler(AuthUser user, String notAnAuthUser) {
        }
    }
}
