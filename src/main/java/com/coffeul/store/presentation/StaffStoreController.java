package com.coffeul.store.presentation;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import com.coffeul.common.response.ApiResponse;
import com.coffeul.store.application.StoreQueryService;
import com.coffeul.store.application.StoreStatusService;
import com.coffeul.store.domain.Store;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** MS-7(내가 관리하는 매장) · MS-8(영업 상태 변경) 참조 구현 — 관리자앱. */
@RestController
public class StaffStoreController {

    private static final ZoneOffset KST = ZoneOffset.of("+09:00");

    private final StoreQueryService storeQueryService;
    private final StoreStatusService storeStatusService;

    public StaffStoreController(StoreQueryService storeQueryService, StoreStatusService storeStatusService) {
        this.storeQueryService = storeQueryService;
        this.storeStatusService = storeStatusService;
    }

    @GetMapping("/api/v1/staff/stores")
    public ApiResponse<List<StaffStoreResponse>> myStores(AuthUser authUser) {
        requireStaffOrOwner(authUser);
        List<StaffStoreResponse> stores = storeQueryService.listForStaff(authUser).stream()
                .map(item -> new StaffStoreResponse(item.store().getId(), item.store().getName(),
                        item.schoolName(), item.store().getStatus()))
                .toList();
        return ApiResponse.success("매장 목록을 불러왔어요.", stores);
    }

    @PatchMapping("/api/v1/staff/stores/{storeId}/status")
    public ApiResponse<StoreStatusChangeResponse> changeStatus(AuthUser authUser, @PathVariable Long storeId,
                                                                 @RequestBody StoreStatusChangeRequest request) {
        Store store = storeStatusService.changeStatus(authUser, storeId, request.status(), request.forceOrDefault());
        return ApiResponse.success("영업 상태를 바꿨어요.",
                new StoreStatusChangeResponse(store.getId(), store.getStatus(), OffsetDateTime.ofInstant(Instant.now(), KST)));
    }

    /** MS-7의 권한은 STAFF · OWNER다(ADMIN·CUSTOMER 제외) — api.py 명세 그대로. */
    private void requireStaffOrOwner(AuthUser authUser) {
        boolean allowed = authUser.type() == AuthUser.SubjectType.STAFF
                && (authUser.role() == AuthUser.Role.STAFF || authUser.role() == AuthUser.Role.OWNER);
        if (!allowed) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }
}
