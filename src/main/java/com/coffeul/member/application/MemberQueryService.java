package com.coffeul.member.application;

import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import com.coffeul.member.domain.Member;
import com.coffeul.member.infrastructure.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** SM-4(내 정보 조회) — REQ-U-004. */
@Service
public class MemberQueryService {

    private final MemberRepository memberRepository;
    private final SchoolLookupPort schoolLookupPort;

    public MemberQueryService(MemberRepository memberRepository, SchoolLookupPort schoolLookupPort) {
        this.memberRepository = memberRepository;
        this.schoolLookupPort = schoolLookupPort;
    }

    @Transactional(readOnly = true)
    public Result findMe(Long memberId) {
        Member member = activeMember(memberId);

        // 학교 행이 사라지는 경우는 사실상 없지만(FK가 막는다), 없으면 이름 대신 null을 주는 대신
        // 500으로 터뜨리지 않고 빈 값으로 내려보낸다 — 내 정보 화면이 학교 때문에 통째로 실패하면 안 된다.
        SchoolLookupPort.SchoolInfo school = schoolLookupPort.findById(member.getSchoolId())
                .orElseGet(() -> new SchoolLookupPort.SchoolInfo(member.getSchoolId(), null, null));

        return new Result(member.getId(), member.getName(), member.getEmail(), school, member.getCreatedAt());
    }

    /**
     * 토큰은 유효하지만 계정이 더는 쓸 수 없는 상태인 경우를 C002로 막는다.
     *
     * <p>REQ-AUTH-012: 탈퇴 · 정지 계정은 refresh를 폐기해도 access 토큰이 최대 1시간 살아 있다. 그 사이
     * 이 API를 부르면 탈퇴로 비워진 값(email=null, name='탈퇴회원')이 그대로 내려간다. 명세가 이 API의
     * 실패를 C002 하나로만 정의해 뒀으므로 같은 코드로 막는다.
     */
    private Member activeMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        if (!member.isActive()) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return member;
    }

    public record Result(Long memberId, String name, String email,
                         SchoolLookupPort.SchoolInfo school, Instant createdAt) {
    }
}
