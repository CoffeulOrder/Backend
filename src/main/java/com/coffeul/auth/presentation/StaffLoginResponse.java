package com.coffeul.auth.presentation;

import java.util.List;

/** MS-2 응답. */
public record StaffLoginResponse(String accessToken, String refreshToken, long expiresIn,
                                  StaffSummary staff, List<StoreSummary> stores) {

    public record StaffSummary(Long staffId, String name, String role) {
    }

    public record StoreSummary(Long storeId, String name, String status) {
    }
}
