package com.coffeul.store.presentation;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import com.coffeul.common.response.ApiResponse;
import com.coffeul.store.application.StoreQueryService;
import com.coffeul.store.domain.Merchant;
import com.coffeul.store.domain.Store;
import com.coffeul.store.domain.StoreBusinessHour;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** MS-5(우리 학교 매장 목록) · MS-6(매장 상세 · 판매자 정보) 참조 구현 — 고객앱. */
@RestController
public class StoreController {

    private final StoreQueryService storeQueryService;

    public StoreController(StoreQueryService storeQueryService) {
        this.storeQueryService = storeQueryService;
    }

    @GetMapping("/api/v1/stores")
    public ApiResponse<List<StoreSummaryResponse>> list(AuthUser authUser) {
        Long schoolId = customerSchoolId(authUser);
        List<StoreSummaryResponse> stores = storeQueryService.listBySchool(schoolId).stream()
                .map(store -> new StoreSummaryResponse(store.getId(), store.getName(), store.getLocation(), store.getStatus()))
                .toList();
        return ApiResponse.success("매장 목록을 불러왔어요.", stores);
    }

    @GetMapping("/api/v1/stores/{storeId}")
    public ApiResponse<StoreDetailResponse> detail(AuthUser authUser, @PathVariable Long storeId) {
        Long schoolId = customerSchoolId(authUser);
        StoreQueryService.StoreDetail detail = storeQueryService.getDetail(storeId, schoolId);
        return ApiResponse.success("매장 정보를 불러왔어요.", toResponse(detail));
    }

    private StoreDetailResponse toResponse(StoreQueryService.StoreDetail detail) {
        Store store = detail.store();
        Merchant merchant = detail.merchant();
        List<StoreDetailResponse.BusinessHourItem> businessHours = detail.businessHours().stream()
                .map(this::toBusinessHourItem)
                .toList();
        StoreDetailResponse.SellerInfo seller = new StoreDetailResponse.SellerInfo(
                merchant.getBusinessName(), merchant.getRepresentativeName(), merchant.getBusinessRegNo(),
                merchant.getBusinessAddress(), merchant.getContactPhone(), merchant.getMailOrderRegNo());
        return new StoreDetailResponse(store.getId(), store.getName(), store.getLocation(), store.getStatus(),
                store.getNotice(), businessHours, seller);
    }

    private StoreDetailResponse.BusinessHourItem toBusinessHourItem(StoreBusinessHour hour) {
        return new StoreDetailResponse.BusinessHourItem(hour.getDayOfWeek(), hour.getOpenTime(), hour.getCloseTime(), hour.isClosed());
    }

    /** MS-5·6의 권한은 CUSTOMER다 — 직원 토큰의 sub는 staff_account.id라 그대로 쓰면 남의 학교 매장이 섞일 수 있다. */
    private Long customerSchoolId(AuthUser authUser) {
        if (authUser.type() != AuthUser.SubjectType.MEMBER || authUser.role() != AuthUser.Role.CUSTOMER) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return authUser.schoolId();
    }
}
