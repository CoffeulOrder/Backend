package com.coffeul.auth.presentation;

import com.coffeul.auth.application.LogoutService;
import com.coffeul.auth.application.MemberLoginService;
import com.coffeul.auth.application.StaffLoginService;
import com.coffeul.auth.application.TokenRefreshService;
import com.coffeul.auth.infrastructure.JwtAccessTokenService;
import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import com.coffeul.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/** MS-1~4 참조 구현 (rules.py AUTH_SPEC). */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final MemberLoginService memberLoginService;
    private final StaffLoginService staffLoginService;
    private final TokenRefreshService tokenRefreshService;
    private final LogoutService logoutService;
    private final JwtAccessTokenService jwtAccessTokenService;

    public AuthController(MemberLoginService memberLoginService,
                           StaffLoginService staffLoginService,
                           TokenRefreshService tokenRefreshService,
                           LogoutService logoutService,
                           JwtAccessTokenService jwtAccessTokenService) {
        this.memberLoginService = memberLoginService;
        this.staffLoginService = staffLoginService;
        this.tokenRefreshService = tokenRefreshService;
        this.logoutService = logoutService;
        this.jwtAccessTokenService = jwtAccessTokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<MemberLoginResponse>> login(@Valid @RequestBody MemberLoginRequest request) {
        MemberLoginService.Result result = memberLoginService.login(request.email(), request.password());
        MemberLoginResponse body = new MemberLoginResponse(
                result.tokens().accessToken(), result.tokens().refreshToken(), result.tokens().expiresIn(),
                new MemberLoginResponse.MemberSummary(result.memberId(), result.name(), result.schoolId()));
        return ResponseEntity.ok(ApiResponse.success("로그인했어요.", body));
    }

    @PostMapping("/staff/login")
    public ResponseEntity<ApiResponse<StaffLoginResponse>> staffLogin(@Valid @RequestBody StaffLoginRequest request) {
        StaffLoginService.Result result = staffLoginService.login(request.loginId(), request.password());
        List<StaffLoginResponse.StoreSummary> stores = result.stores().stream()
                .map(s -> new StaffLoginResponse.StoreSummary(s.storeId(), s.name(), s.status()))
                .collect(Collectors.toList());
        StaffLoginResponse body = new StaffLoginResponse(
                result.tokens().accessToken(), result.tokens().refreshToken(), result.tokens().expiresIn(),
                new StaffLoginResponse.StaffSummary(result.staffId(), result.name(), result.role()), stores);
        return ResponseEntity.ok(ApiResponse.success("로그인했어요.", body));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        var pair = tokenRefreshService.refresh(request.refreshToken());
        TokenRefreshResponse body = new TokenRefreshResponse(pair.accessToken(), pair.refreshToken(), pair.expiresIn());
        return ResponseEntity.ok(ApiResponse.success("토큰을 재발급했어요.", body));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody LogoutRequest request) {
        jwtAccessTokenService.decode(bearerToken(authorization));
        logoutService.logout(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success("로그아웃했어요."));
    }

    private String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return authorizationHeader.substring("Bearer ".length());
    }
}
