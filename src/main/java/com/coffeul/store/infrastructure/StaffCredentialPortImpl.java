package com.coffeul.store.infrastructure;

import com.coffeul.auth.api.StaffCredentialPort;
import com.coffeul.store.domain.Store;
import com.coffeul.store.domain.StaffAccount;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** auth가 MS-2(직원 로그인)에서 직원 자격을 조회할 때 쓰는 어댑터 (rules.py AUTH_SPEC: 역방향 포트). */
@Component
public class StaffCredentialPortImpl implements StaffCredentialPort {

    private final StaffAccountRepository staffAccountRepository;
    private final StaffStoreRepository staffStoreRepository;
    private final StoreRepository storeRepository;

    public StaffCredentialPortImpl(StaffAccountRepository staffAccountRepository,
                                    StaffStoreRepository staffStoreRepository,
                                    StoreRepository storeRepository) {
        this.staffAccountRepository = staffAccountRepository;
        this.staffStoreRepository = staffStoreRepository;
        this.storeRepository = storeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffCredential> findByLoginId(String loginId) {
        return staffAccountRepository.findByLoginId(loginId).map(this::toCredential);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffCredential> findById(Long staffId) {
        return staffAccountRepository.findById(staffId).map(this::toCredential);
    }

    @Override
    @Transactional
    public void recordLoginSuccess(Long staffId, Instant now) {
        staffAccountRepository.findById(staffId).ifPresent(staff -> staff.recordLoginSuccess(now));
    }

    @Override
    @Transactional
    public void recordLoginFailure(Long staffId, Instant now, Instant lockedUntil) {
        staffAccountRepository.findById(staffId).ifPresent(staff -> staff.recordLoginFailure(lockedUntil));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffStoreSummary> findAccessibleStores(Long staffId) {
        StaffAccount staff = staffAccountRepository.findById(staffId).orElse(null);
        if (staff == null) {
            return List.of();
        }
        List<Store> stores = switch (staff.getRole()) {
            case "OWNER" -> storeRepository.findByMerchantId(staff.getMerchantId());
            case "STAFF" -> {
                List<Long> storeIds = staffStoreRepository.findByIdStaffAccountId(staffId).stream()
                        .map(link -> link.getStoreId()).toList();
                yield storeRepository.findAllById(storeIds);
            }
            default -> List.of();
        };
        return stores.stream().map(s -> new StaffStoreSummary(s.getId(), s.getName(), s.getStatus())).toList();
    }

    private StaffCredential toCredential(StaffAccount staff) {
        return new StaffCredential(staff.getId(), staff.getRole(), staff.getMerchantId(), staff.getName(),
                staff.getPasswordHash(), staff.isActive(), staff.getFailedLoginCount(), staff.getLockedUntil());
    }
}
