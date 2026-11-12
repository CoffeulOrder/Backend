package com.coffeul.notification.application;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import com.coffeul.common.response.ApiResponse;
import com.coffeul.notification.domain.ExpoPushTokenFormat;
import com.coffeul.notification.infrastructure.DeviceTokenJdbcRepository;
import com.coffeul.store.api.StoreAccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * TW-1(푸시 토큰 등록 · 갱신) — REQ-NT-001 · 002.
 *
 * <p>검증 순서: 계정 종류와 앱 종류가 맞는지(C003) → 토큰 형식(NT001) → 매장 지정 규칙(C001) → 매장 접근 권한(AUTH_004).
 * 권한을 먼저 보는 이유는 고객 토큰으로 관리자앱 등록을 시도하는 요청에 입력 오류 상세를 알려줄 필요가 없어서다.
 */
@Service
public class DeviceTokenRegisterService {

    public static final String APP_CUSTOMER = "CUSTOMER";
    public static final String APP_MANAGER = "MANAGER";

    private final DeviceTokenJdbcRepository deviceTokenRepository;
    private final StoreAccessPolicy storeAccessPolicy;

    public DeviceTokenRegisterService(DeviceTokenJdbcRepository deviceTokenRepository,
                                       StoreAccessPolicy storeAccessPolicy) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.storeAccessPolicy = storeAccessPolicy;
    }

    public record Command(String expoPushToken, String platform, String appType, Long storeId) {
    }

    public record Result(Long deviceTokenId, boolean active) {
    }

    @Transactional
    public Result register(AuthUser authUser, Command command) {
        boolean managerApp = APP_MANAGER.equals(command.appType());
        requireAccountMatchesApp(authUser, managerApp);

        if (!ExpoPushTokenFormat.isValid(command.expoPushToken())) {
            throw new BusinessException(NotificationErrorCode.INVALID_PUSH_TOKEN);
        }

        if (managerApp) {
            if (command.storeId() == null) {
                throw invalid("storeId", "매장을 선택해주세요.");
            }
            storeAccessPolicy.check(authUser, command.storeId());
        } else if (command.storeId() != null) {
            // 고객앱 토큰은 매장이 없다 (ck_device_token_app_owner) — 조용히 버리면 클라이언트 실수가 묻힌다.
            throw invalid("storeId", "고객앱은 매장을 지정할 수 없어요.");
        }

        Long id = deviceTokenRepository.upsert(authUser.type().name(), authUser.id(), command.appType(),
                managerApp ? command.storeId() : null, command.expoPushToken(), command.platform(), Instant.now());
        return new Result(id, true);
    }

    /** 고객앱은 MEMBER만, 관리자앱은 STAFF · OWNER만. ADMIN은 앱을 쓰지 않아 어느 쪽도 아니다. */
    private void requireAccountMatchesApp(AuthUser authUser, boolean managerApp) {
        boolean allowed = managerApp
                ? authUser.type() == AuthUser.SubjectType.STAFF
                        && (authUser.role() == AuthUser.Role.STAFF || authUser.role() == AuthUser.Role.OWNER)
                : authUser.type() == AuthUser.SubjectType.MEMBER;
        if (!allowed) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    private BusinessException invalid(String field, String reason) {
        return new BusinessException(CommonErrorCode.VALIDATION_FAILED, reason,
                List.of(new ApiResponse.FieldError(field, reason)));
    }
}
