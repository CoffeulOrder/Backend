package com.coffeul.member.presentation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MemberRegisterRequest(
        @NotBlank(message = "이메일 인증을 다시 진행해주세요.")
        String verificationToken,

        @NotBlank(message = "이름을 입력해주세요.")
        @Size(max = 30, message = "이름은 30자 이하로 입력해주세요.")
        String name,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        String password,

        @NotEmpty(message = "필수 약관에 동의해주세요.")
        List<@Valid AgreementRequest> agreements
) {

    public record AgreementRequest(
            @NotBlank(message = "약관 종류가 필요해요.")
            String type,

            @NotBlank(message = "약관 버전이 필요해요.")
            @Size(max = 20, message = "약관 버전 형식이 올바르지 않아요.")
            String version,

            boolean agreed
    ) {
    }
}
