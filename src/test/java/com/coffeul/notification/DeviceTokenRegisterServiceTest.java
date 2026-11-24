package com.coffeul.notification;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.common.error.BusinessException;
import com.coffeul.notification.application.DeviceTokenRegisterService;
import com.coffeul.notification.infrastructure.DeviceTokenJdbcRepository;
import com.coffeul.store.api.StoreAccessPolicy;
import com.coffeul.store.api.StoreErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** TW-1 검증 순서와 계정 · 앱 종류 규칙. DB 없이 돈다 — 실제 upsert는 DevicePushTokenIntegrationTest가 본다. */
class DeviceTokenRegisterServiceTest {

    private static final String TOKEN = "ExponentPushToken[abc123]";

    private DeviceTokenJdbcRepository repository;
    private StoreAccessPolicy storeAccessPolicy;
    private DeviceTokenRegisterService service;

    private final AuthUser customer = new AuthUser(7L, AuthUser.SubjectType.MEMBER, AuthUser.Role.CUSTOMER, 1L, null);
    private final AuthUser staff = new AuthUser(8L, AuthUser.SubjectType.STAFF, AuthUser.Role.STAFF, null, 3L);
    private final AuthUser owner = new AuthUser(9L, AuthUser.SubjectType.STAFF, AuthUser.Role.OWNER, null, 3L);
    private final AuthUser admin = new AuthUser(1L, AuthUser.SubjectType.STAFF, AuthUser.Role.ADMIN, null, null);

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(DeviceTokenJdbcRepository.class);
        storeAccessPolicy = Mockito.mock(StoreAccessPolicy.class);
        service = new DeviceTokenRegisterService(repository, storeAccessPolicy);
        when(repository.upsert(any(), any(), any(), any(), any(), any(), any())).thenReturn(88L);
    }

    private DeviceTokenRegisterService.Command command(String token, String appType, Long storeId) {
        return new DeviceTokenRegisterService.Command(token, "IOS", appType, storeId);
    }

    private void assertCode(Runnable call, String code, int status) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.errorCode().code()).isEqualTo(code);
                    assertThat(e.errorCode().status()).isEqualTo(status);
                });
    }

    @Test
    void 고객은_매장_없이_CUSTOMER로_등록한다() {
        DeviceTokenRegisterService.Result result = service.register(customer, command(TOKEN, "CUSTOMER", null));

        assertThat(result.deviceTokenId()).isEqualTo(88L);
        assertThat(result.active()).isTrue();
        verify(repository).upsert(eq("MEMBER"), eq(7L), eq("CUSTOMER"), eq(null), eq(TOKEN), eq("IOS"), any(Instant.class));
        verifyNoInteractions(storeAccessPolicy);
    }

    @Test
    void 직원은_매장과_함께_MANAGER로_등록한다() {
        service.register(staff, command(TOKEN, "MANAGER", 5L));

        verify(storeAccessPolicy).check(staff, 5L);
        verify(repository).upsert(eq("STAFF"), eq(8L), eq("MANAGER"), eq(5L), eq(TOKEN), eq("IOS"), any(Instant.class));
    }

    @Test
    void 사장님도_MANAGER로_등록할_수_있다() {
        service.register(owner, command(TOKEN, "MANAGER", 5L));

        verify(storeAccessPolicy).check(owner, 5L);
        verify(repository).upsert(eq("STAFF"), eq(9L), eq("MANAGER"), eq(5L), eq(TOKEN), eq("IOS"), any(Instant.class));
    }

    @Test
    void 고객이_MANAGER로_등록하면_403_C003이다() {
        assertCode(() -> service.register(customer, command(TOKEN, "MANAGER", 5L)), "C003", 403);
        verify(repository, never()).upsert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void 직원이_CUSTOMER로_등록하면_403_C003이다() {
        assertCode(() -> service.register(staff, command(TOKEN, "CUSTOMER", null)), "C003", 403);
        verify(repository, never()).upsert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void ADMIN은_어느_앱에도_등록할_수_없다() {
        assertCode(() -> service.register(admin, command(TOKEN, "MANAGER", 5L)), "C003", 403);
        assertCode(() -> service.register(admin, command(TOKEN, "CUSTOMER", null)), "C003", 403);
    }

    @Test
    void 토큰_형식이_틀리면_400_NT001이다() {
        assertCode(() -> service.register(customer, command("not-a-token", "CUSTOMER", null)), "NT001", 400);
        verify(repository, never()).upsert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void 관리자앱인데_storeId가_없으면_C001과_필드_오류를_준다() {
        assertThatThrownBy(() -> service.register(staff, command(TOKEN, "MANAGER", null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.errorCode().code()).isEqualTo("C001");
                    assertThat(e.getMessage()).isEqualTo("매장을 선택해주세요.");
                    assertThat(e.errors()).hasSize(1);
                    assertThat(e.errors().get(0).field()).isEqualTo("storeId");
                });
        verifyNoInteractions(storeAccessPolicy);
    }

    @Test
    void 고객앱인데_storeId가_있으면_C001이다() {
        assertCode(() -> service.register(customer, command(TOKEN, "CUSTOMER", 5L)), "C001", 400);
        verify(repository, never()).upsert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void 매장_접근_권한이_없으면_저장하지_않고_AUTH_004를_그대로_던진다() {
        Mockito.doThrow(new BusinessException(StoreErrorCode.FORBIDDEN)).when(storeAccessPolicy).check(staff, 5L);

        assertCode(() -> service.register(staff, command(TOKEN, "MANAGER", 5L)), "AUTH_004", 403);
        verify(repository, never()).upsert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void 권한_검사가_토큰_형식_검사보다_먼저다() {
        assertCode(() -> service.register(customer, command("bad", "MANAGER", null)), "C003", 403);
    }
}
