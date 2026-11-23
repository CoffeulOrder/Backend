package com.coffeul.payment.infrastructure;

import com.coffeul.common.error.BusinessException;
import com.coffeul.payment.api.PaymentErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.util.Map;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

@Component
@ConditionalOnProperty(name = "coffeul.payment.gateway", havingValue = "portone")
public class PortOnePaymentClient {
    private final RestClient client;

    @Autowired
    public PortOnePaymentClient(RestClient.Builder builder, @Value("${coffeul.payment.portone.api-secret:}") String apiSecret) {
        this(configure(builder.requestFactory(requestFactory()), apiSecret).build());
    }

    PortOnePaymentClient(RestClient client) {
        this.client = client;
    }

    static RestClient.Builder configure(RestClient.Builder builder, String apiSecret) {
        Assert.hasText(apiSecret, "coffeul.payment.portone.api-secret is required for the portone gateway");
        return builder.baseUrl("https://api.portone.io")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "PortOne " + apiSecret)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    public record PaidPayment(String paymentId, String transactionId, String storeId, String channelKey,
                              String provider, String version, String currency, long totalAmount,
                              String method, Instant paidAt) { }

    public PaidPayment getPaidPayment(String paymentId, String storeId) {
        try {
            JsonNode body = client.get().uri("/payments/{paymentId}?storeId={storeId}", paymentId, storeId)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (request, response) -> {
                        throw new BusinessException(PaymentErrorCode.NOT_PAID);
                    })
                    .onStatus(status -> !status.is2xxSuccessful(), (request, response) -> {
                        throw new BusinessException(PaymentErrorCode.LOOKUP_UNAVAILABLE);
                    })
                    .body(JsonNode.class);
            if (body == null || !body.path("status").isTextual()) {
                throw new BusinessException(PaymentErrorCode.LOOKUP_UNAVAILABLE);
            }
            if (!"PAID".equals(body.path("status").textValue())) {
                throw new BusinessException(PaymentErrorCode.NOT_PAID);
            }
            return readPaidPayment(body);
        } catch (RestClientException | DateTimeParseException e) {
            // PG 원문에는 구매자 정보가 포함될 수 있으므로 응답이나 예외 본문을 노출하지 않는다.
            throw new BusinessException(PaymentErrorCode.LOOKUP_UNAVAILABLE);
        }
    }

    public record CancellationPayment(String paymentId, String transactionId, String storeId,
                                      String channelKey, int amount) { }

    /** Full refund only. A successful POST alone is insufficient proof of completion. */
    public boolean cancelFullPayment(CancellationPayment payment, String idempotencyKey) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("CANCEL_IO_REQUIRES_NO_TRANSACTION");
        }
        if (payment.amount() <= 0 || !hasText(payment.paymentId()) || !hasText(payment.transactionId())
                || !hasText(payment.storeId()) || !hasText(payment.channelKey())
                || idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9_-]{16,256}")) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CONTEXT_MISMATCH);
        }
        try {
            JsonNode before = getCancellationPayment(payment);
            if (fullyCancelled(before, payment)) {
                return true;
            }
            requireUncancelledPayment(before);
            // An asynchronous cancellation already exists. Poll it without creating another request.
            if (hasPendingCancellation(before, payment.amount())) {
                return false;
            }
            JsonNode result = client.post().uri("/payments/{paymentId}/cancel", payment.paymentId())
                    .header("Idempotency-Key", "\"" + idempotencyKey + "\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("storeId", payment.storeId(), "reason", "고객 요청", "requester", "CUSTOMER",
                            "amount", payment.amount(), "currentCancellableAmount", payment.amount()))
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (request, response) -> {
                        throw new BusinessException(PaymentErrorCode.LOOKUP_UNAVAILABLE);
                    }).body(JsonNode.class);
            if (result == null) {
                throw new BusinessException(PaymentErrorCode.LOOKUP_UNAVAILABLE);
            }
            JsonNode cancellation = result.path("cancellation");
            String status = cancellation.path("status").textValue();
            if (!"SUCCEEDED".equals(status) && !"REQUESTED".equals(status)) {
                throw new BusinessException(PaymentErrorCode.LOOKUP_UNAVAILABLE);
            }
            if (requiredAmount(cancellation, "totalAmount") != payment.amount()) {
                throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
            }
            requiredText(cancellation, "id");
            // Also covers eventual consistency: a still-PAID response stays retryable.
            JsonNode after = getCancellationPayment(payment);
            if (fullyCancelled(after, payment)) {
                return true;
            }
            requireUncancelledPayment(after);
            return false;
        } catch (RestClientException e) {
            throw new BusinessException(PaymentErrorCode.LOOKUP_UNAVAILABLE);
        }
    }

    private JsonNode getCancellationPayment(CancellationPayment expected) {
        JsonNode body = client.get().uri("/payments/{paymentId}?storeId={storeId}",
                        expected.paymentId(), expected.storeId())
                .retrieve().onStatus(status -> !status.is2xxSuccessful(), (request, response) -> {
                    throw new BusinessException(PaymentErrorCode.LOOKUP_UNAVAILABLE);
                }).body(JsonNode.class);
        if (body == null) {
            throw new BusinessException(PaymentErrorCode.LOOKUP_UNAVAILABLE);
        }
        JsonNode channel = body.path("channel");
        if (!expected.paymentId().equals(requiredText(body, "id"))
                || !expected.transactionId().equals(requiredText(body, "transactionId"))
                || !expected.storeId().equals(requiredText(body, "storeId"))
                || !expected.channelKey().equals(requiredText(channel, "key"))
                || !"INICIS_V2".equals(requiredText(channel, "pgProvider"))
                || !"V2".equals(requiredText(body, "version"))
                || !"KRW".equals(requiredText(body, "currency"))) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CONTEXT_MISMATCH);
        }
        if (requiredAmount(body.path("amount"), "total") != expected.amount()) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
        return body;
    }

    private boolean fullyCancelled(JsonNode body, CancellationPayment payment) {
        if (!"CANCELLED".equals(body.path("status").textValue())) {
            return false;
        }
        if (requiredAmount(body.path("amount"), "cancelled") != payment.amount()) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
        return true;
    }

    private void requireUncancelledPayment(JsonNode body) {
        if (!"PAID".equals(body.path("status").textValue())) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CONTEXT_MISMATCH);
        }
        if (requiredAmount(body.path("amount"), "cancelled") != 0) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
    }

    private boolean hasPendingCancellation(JsonNode body, int amount) {
        for (JsonNode cancellation : body.path("cancellations")) {
            if ("REQUESTED".equals(cancellation.path("status").textValue())) {
                if (requiredAmount(cancellation, "totalAmount") != amount) {
                    throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
                }
                return true;
            }
        }
        return false;
    }

    private long requiredAmount(JsonNode body, String key) {
        JsonNode amount = body.path(key);
        if (!amount.isIntegralNumber() || !amount.canConvertToLong() || amount.longValue() < 0) {
            throw new BusinessException(PaymentErrorCode.LOOKUP_UNAVAILABLE);
        }
        return amount.longValue();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private PaidPayment readPaidPayment(JsonNode body) {
        JsonNode total = body.path("amount").path("total");
        if (!total.isIntegralNumber() || !total.canConvertToLong()) {
            throw new BusinessException(PaymentErrorCode.LOOKUP_UNAVAILABLE);
        }
        JsonNode channel = body.path("channel");
        return new PaidPayment(requiredText(body, "id"), requiredText(body, "transactionId"),
                requiredText(body, "storeId"), requiredText(channel, "key"), requiredText(channel, "pgProvider"),
                requiredText(body, "version"), requiredText(body, "currency"), total.longValue(),
                body.path("method").path("type").textValue(), Instant.parse(requiredText(body, "paidAt")));
    }

    private String requiredText(JsonNode node, String key) {
        String value = node.path(key).textValue();
        if (value == null || value.isBlank()) {
            throw new BusinessException(PaymentErrorCode.LOOKUP_UNAVAILABLE);
        }
        return value;
    }
}
