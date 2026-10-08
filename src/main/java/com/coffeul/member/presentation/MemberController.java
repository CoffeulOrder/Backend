package com.coffeul.member.presentation;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import com.coffeul.common.response.ApiResponse;
import com.coffeul.member.application.MemberQueryService;
import com.coffeul.member.application.MemberRegisterService;
import com.coffeul.member.application.MemberWithdrawService;
import com.coffeul.member.application.PasswordChangeService;
import com.coffeul.member.application.PasswordResetService;
import com.coffeul.member.application.SchoolLookupPort;
import com.coffeul.member.domain.TermsType;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/** SM-3 (회원가입) · SM-4 (내 정보) · SM-5 (비밀번호 변경) · SM-6 (비밀번호 재설정) · SM-7 (탈퇴). */
@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberRegisterService memberRegisterService;
    private final PasswordResetService passwordResetService;
    private final MemberQueryService memberQueryService;
    private final PasswordChangeService passwordChangeService;
    private final MemberWithdrawService memberWithdrawService;
    private final ZoneId businessZone;

    public MemberController(MemberRegisterService memberRegisterService,
                             PasswordResetService passwordResetService,
                             MemberQueryService memberQueryService,
                             PasswordChangeService passwordChangeService,
                             MemberWithdrawService memberWithdrawService,
                             @Value("${coffeul.business-zone:Asia/Seoul}") String businessZone) {
        this.memberRegisterService = memberRegisterService;
        this.passwordResetService = passwordResetService;
        this.memberQueryService = memberQueryService;
        this.passwordChangeService = passwordChangeService;
        this.memberWithdrawService = memberWithdrawService;
        this.businessZone = ZoneId.of(businessZone);
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

    /** SM-4 (REQ-U-004). */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyInfoResponse>> me(AuthUser authUser) {
        MemberQueryService.Result result = memberQueryService.findMe(customerId(authUser));

        SchoolLookupPort.SchoolInfo school = result.school();
        MyInfoResponse body = new MyInfoResponse(
                result.memberId(), result.name(), result.email(),
                new MyInfoResponse.SchoolSummary(school.schoolId(), school.name(), school.campus()),
                atBusinessZone(result.createdAt()));
        return ResponseEntity.ok(ApiResponse.success("내 정보를 불러왔어요.", body));
    }

    /** SM-5 (REQ-U-005). */
    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(AuthUser authUser,
                                                             @Valid @RequestBody PasswordChangeRequest request) {
        passwordChangeService.change(customerId(authUser), request.currentPassword(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.success("비밀번호를 바꿨어요. 다시 로그인해주세요."));
    }

    /** SM-7 (REQ-U-007). */
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> withdraw(AuthUser authUser,
                                                       @Valid @RequestBody MemberWithdrawRequest request) {
        memberWithdrawService.withdraw(customerId(authUser), request.password());
        return ResponseEntity.ok(ApiResponse.success("탈퇴했어요. 그동안 이용해주셔서 고마워요."));
    }

    /**
     * 이 세 API의 권한은 CUSTOMER다. 직원 토큰(typ=STAFF)의 sub는 staff_account.id인데, 그대로 쓰면
     * 같은 숫자의 member.id를 남의 정보로 읽거나 탈퇴시켜 버린다. 그래서 주체 종류를 먼저 확인한다.
     */
    private Long customerId(AuthUser authUser) {
        if (authUser.type() != AuthUser.SubjectType.MEMBER || authUser.role() != AuthUser.Role.CUSTOMER) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return authUser.id();
    }

    // DB엔 UTC로 들어 있고 명세의 응답 예시는 +09:00이다. 서버 시간대에 흔들리지 않게 영업 시간대로 못 박는다.
    private OffsetDateTime atBusinessZone(Instant instant) {
        return instant == null ? null : instant.atZone(businessZone).toOffsetDateTime();
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
