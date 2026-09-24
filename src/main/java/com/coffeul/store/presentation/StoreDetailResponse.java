package com.coffeul.store.presentation;

import java.time.LocalTime;
import java.util.List;

public record StoreDetailResponse(Long storeId, String name, String location, String status, String notice,
                                   List<BusinessHourItem> businessHours, SellerInfo seller) {

    public record BusinessHourItem(int dayOfWeek, LocalTime openTime, LocalTime closeTime, boolean closed) {
    }

    public record SellerInfo(String businessName, String representativeName, String businessRegNo,
                              String businessAddress, String contactPhone, String mailOrderRegNo) {
    }
}
