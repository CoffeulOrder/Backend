package com.coffeul.order.application;

import com.coffeul.order.presentation.OrderCreateRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Collectors;

/**
 * 같은 Idempotency-Key로 다른 내용의 주문이 오면 OD010으로 막기 위한 요청 본문 해시.
 * (rules.py: "Idempotency-Key + 정규화한 요청 본문 SHA-256(request_hash)로 재요청 판별")
 */
final class RequestHash {

    private RequestHash() {
    }

    static String of(OrderCreateRequest request) {
        String canonical = request.storeId() + "|"
                + request.items().stream()
                        .map(i -> i.menuId() + ":" + i.quantity() + ":"
                                + (i.optionItemIds() == null ? "" : i.optionItemIds().stream()
                                        .sorted().map(String::valueOf).collect(Collectors.joining(","))))
                        .collect(Collectors.joining(";"))
                + "|" + (request.requestMemo() == null ? "" : request.requestMemo())
                + "|" + request.withdrawalLimitAgreed();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no available", e);
        }
    }
}
