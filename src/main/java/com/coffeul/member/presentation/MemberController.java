package com.coffeul.member.presentation;

import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import com.coffeul.common.response.ApiResponse;
import com.coffeul.member.application.MemberRegisterService;
import com.coffeul.member.application.PasswordResetService;
import com.coffeul.member.application.SchoolLookupPort;
import com.coffeul.member.domain.TermsType;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** SM-3 (회원가입) · SM-6 (비밀번호 재설정). SM-4 · 5 · 7은 AuthUser 인자 리졸버가 나온 뒤에 여기에 붙인다. */
@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberRegisterService memberRegisterService;
    private final PasswordResetService passwordResetService;

    public MemberController(MemberRegisterService memberRegisterService,
                             PasswordResetService passwordResetService) {
        this.memberRegisterService = memberRegisterService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MemberRegisterResponse>> register(
            @Valid @RequestBody MemberRegisterRequest request) {
        MemberRegisterService.Result result = memberRegisterService.register(toCommand(request));

        SchoolLookupPort.SchoolInfo school = result.school();
        MemberRegisterResponse body = new MemberRegisterResponse(
                result.memberId(), result.name(), result.email(),
                new MemberRegisterResponse.SchoolSummary(school.schoolId(), school.name(), school.campus()),
                result.tokens().accessToken(), result.tokens().refreshToken(), result.tokens().expiresIn());

        ApiResponse<MemberRegisterResponse> response =
                new ApiResponse<>(java.time.OffsetDateTime.now(), 201, "SUCCESS", "가입을 환영해요.", body, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/password-reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.reset(request.verificationToken(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.success("비밀번호를 재설정했어요. 새 비밀번호로 로그인해주세요."));
    }

    private MemberRegisterService.Command toCommand(MemberRegisterRequest request) {
        List<MemberRegisterService.Command.Agreement> agreements = request.agreements().stream()
                .map(agreement -> new MemberRegisterService.Command.Agreement(
                        termsTypeOf(agreement.type()), agreement.version(), agreement.agreed()))
                .toList();
        return new MemberRegisterService.Command(
                request.verificationToken(), request.name(), request.password(), agreements);
    }

    // 약관 종류를 enum 파라미터로 받으면 Jackson이 먼저 터져서 공통 검증 응답(C001) 대신 500이 나간다.
    private TermsType termsTypeOf(String raw) {
        return TermsType.parse(raw)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.VALIDATION_FAILED,
                        CommonErrorCode.VALIDATION_FAILED.message(),
                        List.of(new ApiResponse.FieldError("agreements.type", "SERVICE 또는 PRIVACY여야 해요."))));
    }
}
