package com.coffeul.notification.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DevicePushTokenRequest(
        @NotBlank(message = "푸시 토큰을 보내주세요.")
        String expoPushToken,

        @NotBlank(message = "플랫폼을 보내주세요.")
        @Pattern(regexp = "IOS|ANDROID", message = "플랫폼은 IOS 또는 ANDROID여야 해요.")
        String platform,

        @NotBlank(message = "앱 종류를 보내주세요.")
        @Pattern(regexp = "CUSTOMER|MANAGER", message = "앱 종류는 CUSTOMER 또는 MANAGER여야 해요.")
        String appType,

        Long storeId
) {
}
