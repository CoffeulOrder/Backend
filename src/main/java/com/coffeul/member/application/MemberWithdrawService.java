package com.coffeul.member.application;

import com.coffeul.auth.api.RefreshTokenRevoker;
import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import com.coffeul.member.api.ActiveOrderCountPort;
import com.coffeul.member.domain.Member;
import com.coffeul.member.infrastructure.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/** SM-7(회원 탈퇴) — REQ-U-007. */
@Service
public class MemberWithdrawService {

    private final MemberRepository memberRepository;
    private final ActiveOrderCountPort activeOrderCountPort;
    private final RefreshTokenRevoker refreshTokenRevoker;
    private final PushTokenDeactivationPort pushTokenDeactivationPort;
    private final PasswordEncoder passwordEncoder;

    public MemberWithdrawService(MemberRepository memberRepository,
                                  ActiveOrderCountPort activeOrderCountPort,
                                  RefreshTokenRevoker refreshTokenRevoker,
                                  PushTokenDeactivationPort pushTokenDeactivationPort,
                                  PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.activeOrderCountPort = activeOrderCountPort;
        this.refreshTokenRevoker = refreshTokenRevoker;
        this.pushTokenDeactivationPort = pushTokenDeactivationPort;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 개인정보 삭제 · refresh 폐기 · 푸시 토큰 비활성이 한 트랜잭션 (REQ-U-007: "한 번에").
     * 하나라도 실패하면 탈퇴 자체가 없던 일이 된다 — 계정은 사라졌는데 푸시는 계속 가는 상태를 만들지 않는다.
     */
    @Transactional
    public void withdraw(Long memberId, String password) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));

        // 이미 탈퇴한 계정에 남은 access 토큰으로 다시 부르는 경우. password_hash가 NULL이라 아래에서
        // U004가 나가는데, 그건 "비밀번호가 틀렸다"는 뜻이라 오해를 부른다.
        if (!member.isActive()) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        if (!passwordEncoder.matches(password, member.getPasswordHash())) {
            throw new BusinessException(MemberErrorCode.PASSWORD_MISMATCH);
        }

        // 결제 대기 ~ 픽업 대기 주문이 남아 있으면 막는다. 돈이 걸린 주문을 두고 계정을 비우면
        // 환불 · 픽업 연락이 갈 곳이 없어진다.
        long activeOrderCount = activeOrderCountPort.countActiveOrders(memberId);
        if (activeOrderCount > 0) {
            throw BusinessException.withData(MemberErrorCode.ACTIVE_ORDER_EXISTS,
                    MemberErrorCode.ACTIVE_ORDER_EXISTS.message(),
                    Map.of("activeOrderCount", activeOrderCount));
        }

        member.withdraw(Instant.now());
        refreshTokenRevoker.revokeAllForMember(memberId);
        pushTokenDeactivationPort.deactivateAllForMember(memberId);
    }
}
