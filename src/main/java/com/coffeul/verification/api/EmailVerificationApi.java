package com.coffeul.verification.api;

/**
 * 인증 토큰을 실제로 쓰는 쪽(member의 SM-3 가입 · SM-6 비밀번호 재설정)이 호출하는 접점.
 * 토큰은 한 번만 쓸 수 있고(consumed_at), 용도가 다르면 통하지 않는다 (REQ-EV-005).
 */
public interface EmailVerificationApi {

    /**
     * 토큰을 소비하고 그 토큰에 묶인 이메일과 학교를 돌려준다.
     * 이메일을 요청 본문으로 받지 않고 여기서 꺼내는 이유는 다른 이메일로 바꿔치기하는 걸 막기 위해서다.
     *
     * @throws com.coffeul.common.error.BusinessException 만료 · 이미 사용 · 용도 불일치면 EV008
     */
    VerifiedEmail consume(String verificationToken, VerificationPurpose purpose);

    record VerifiedEmail(String email, Long schoolId) {
    }
}
