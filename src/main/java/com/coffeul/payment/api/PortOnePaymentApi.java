package com.coffeul.payment.api;

import java.time.Instant;
import java.util.Optional;

public interface PortOnePaymentApi {
    Attempt findAttempt(Long orderId, String paymentId);

    Optional<Attempt> findAttemptByPaymentId(String paymentId);

    Attempt lockAttempt(Long orderId, String paymentId);

    Approval verify(Attempt attempt);

    void recordApproval(Attempt attempt, Approval approval);

    record Attempt(long id, long orderId, String paymentId, String status, int amount, String storeId, String channelKey,
                   String method, Instant approvedAt) {
        public boolean approved() {
            return "APPROVED".equals(status);
        }

        public boolean awaitingConfirmation() {
            return "READY".equals(status) || "UNKNOWN".equals(status);
        }
    }

    record Approval(String transactionId, String method, Instant paidAt) { }
}
