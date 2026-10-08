package com.coffeul.store.application;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.common.error.BusinessException;
import com.coffeul.store.api.StoreErrorCode;
import com.coffeul.store.domain.Merchant;
import com.coffeul.store.domain.School;
import com.coffeul.store.domain.Store;
import com.coffeul.store.domain.StoreBusinessHour;
import com.coffeul.store.infrastructure.MerchantRepository;
import com.coffeul.store.infrastructure.SchoolRepository;
import com.coffeul.store.infrastructure.StaffStoreRepository;
import com.coffeul.store.infrastructure.StoreBusinessHourRepository;
import com.coffeul.store.infrastructure.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** MS-5(매장 목록) · MS-6(매장 상세) · MS-7(내가 관리하는 매장) 조회 전용. */
@Service
public class StoreQueryService {

    private final StoreRepository storeRepository;
    private final StoreBusinessHourRepository storeBusinessHourRepository;
    private final MerchantRepository merchantRepository;
    private final StaffStoreRepository staffStoreRepository;
    private final SchoolRepository schoolRepository;

    public StoreQueryService(StoreRepository storeRepository,
                              StoreBusinessHourRepository storeBusinessHourRepository,
                              MerchantRepository merchantRepository,
                              StaffStoreRepository staffStoreRepository,
                              SchoolRepository schoolRepository) {
        this.storeRepository = storeRepository;
        this.storeBusinessHourRepository = storeBusinessHourRepository;
        this.merchantRepository = merchantRepository;
        this.staffStoreRepository = staffStoreRepository;
        this.schoolRepository = schoolRepository;
    }

    /** MS-5: 로그인한 고객 학교의 매장 목록. */
    @Transactional(readOnly = true)
    public List<Store> listBySchool(Long schoolId) {
        return storeRepository.findBySchoolId(schoolId);
    }

    public record StoreDetail(Store store, List<StoreBusinessHour> businessHours, Merchant merchant) {
    }

    /**
     * MS-6: 매장 상세 · 판매자 정보. 다른 학교 매장이면 "없는 매장"과 구분하지 않고 404로 합친다
     * (api.py memo: "다른 학교 매장도 404" — 남의 학교 매장 존재 여부를 흘리지 않는다).
     */
    @Transactional(readOnly = true)
    public StoreDetail getDetail(Long storeId, Long schoolId) {
        Store store = storeRepository.findById(storeId)
                .filter(s -> s.getSchoolId().equals(schoolId))
                .orElseThrow(() -> new BusinessException(StoreErrorCode.NOT_FOUND));
        List<StoreBusinessHour> businessHours = storeBusinessHourRepository.findByIdStoreIdOrderByIdDayOfWeek(storeId);
        Merchant merchant = merchantRepository.findById(store.getMerchantId())
                .orElseThrow(() -> new BusinessException(StoreErrorCode.NOT_FOUND));
        return new StoreDetail(store, businessHours, merchant);
    }

    public record StaffStoreItem(Store store, String schoolName) {
    }

    /** MS-7: STAFF는 배정된 매장, OWNER는 자기 매장 전부. */
    @Transactional(readOnly = true)
    public List<StaffStoreItem> listForStaff(AuthUser staff) {
        List<Store> stores = switch (staff.role()) {
            case OWNER -> storeRepository.findByMerchantId(staff.merchantId());
            case STAFF -> {
                List<Long> storeIds = staffStoreRepository.findByIdStaffAccountId(staff.id()).stream()
                        .map(link -> link.getStoreId()).toList();
                yield storeRepository.findAllById(storeIds);
            }
            default -> List.of();
        };
        return stores.stream().map(store -> new StaffStoreItem(store, schoolNameOf(store.getSchoolId()))).toList();
    }

    private String schoolNameOf(Long schoolId) {
        return schoolRepository.findById(schoolId).map(School::getName).orElse(null);
    }
}
