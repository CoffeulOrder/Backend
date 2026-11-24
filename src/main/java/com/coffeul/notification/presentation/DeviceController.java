package com.coffeul.notification.presentation;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.common.response.ApiResponse;
import com.coffeul.notification.application.DeviceTokenRegisterService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** TW-1(푸시 토큰 등록 · 갱신) — 고객앱 · 관리자앱 로그인 직후, 관리자앱 매장 변경 시 자동 호출. */
@RestController
public class DeviceController {

    private final DeviceTokenRegisterService deviceTokenRegisterService;

    public DeviceController(DeviceTokenRegisterService deviceTokenRegisterService) {
        this.deviceTokenRegisterService = deviceTokenRegisterService;
    }

    @PutMapping("/api/v1/devices/push-token")
    public ApiResponse<DevicePushTokenResponse> registerPushToken(AuthUser authUser,
                                                                  @Valid @RequestBody DevicePushTokenRequest request) {
        DeviceTokenRegisterService.Result result = deviceTokenRegisterService.register(authUser,
                new DeviceTokenRegisterService.Command(request.expoPushToken(), request.platform(),
                        request.appType(), request.storeId()));
        return ApiResponse.success("알림을 받을 준비가 됐어요.",
                new DevicePushTokenResponse(result.deviceTokenId(), result.active()));
    }
}
