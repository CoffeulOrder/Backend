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
