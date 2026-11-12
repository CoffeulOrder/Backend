package com.coffeul.payment.infrastructure;

import com.coffeul.common.error.BusinessException;
import com.coffeul.payment.api.PaymentErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PortOnePaymentClientTest {
    private static final String URL = "https://api.portone.io/payments/payment-test?storeId=store-test";
    private static final String PAID = """
            {"status":"PAID", "id":"payment-test", "transactionId":"tx-test", "version":"V2",
             "storeId":"store-test", "currency":"KRW", "amount":{"total":4500},
             "channel":{"key":"channel-test","pgProvider":"INICIS_V2"}, "method":{"type":"CARD"},
             "paidAt":"2026-11-12T12:00:00+09:00"}
            """;

    private MockRestServiceServer server;
    private PortOnePaymentClient client;

    @BeforeEach
    void setUp() {
        var builder = PortOnePaymentClient.configure(RestClient.builder(), "unit-test-placeholder");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PortOnePaymentClient(builder.build());
    }

    @Test
    void authenticatesServerLookupAndParsesApprovedPayment() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "PortOne unit-test-placeholder"))
                .andRespond(withSuccess(PAID, MediaType.APPLICATION_JSON));
        assertThat(client.getPaidPayment("payment-test", "store-test")).isEqualTo(
                new PortOnePaymentClient.PaidPayment("payment-test", "tx-test", "store-test", "channel-test",
                        "INICIS_V2", "V2", "KRW", 4500, "CARD", Instant.parse("2026-11-12T03:00:00Z")));
        server.verify();
    }

    @Test
    void pendingStatusCannotBeTreatedAsPaid() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"status\":\"READY\"}", MediaType.APPLICATION_JSON));
        assertLookupFails(PaymentErrorCode.NOT_PAID);
    }

    @Test
    void providerNotFoundIsAnUnconfirmedPayment() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertLookupFails(PaymentErrorCode.NOT_PAID);
    }

    @Test
    void providerFailureReturnsRetryableError() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertLookupFails(PaymentErrorCode.LOOKUP_UNAVAILABLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"4500.5", "\"4500\"", "null"})
    void malformedAmountDoesNotGetCoerced(String amount) {
        server.expect(requestTo(URL)).andRespond(withSuccess(PAID.replace("4500", amount), MediaType.APPLICATION_JSON));
        assertLookupFails(PaymentErrorCode.LOOKUP_UNAVAILABLE);
    }

    private void assertLookupFails(PaymentErrorCode expected) {
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> client.getPaidPayment("payment-test", "store-test"))
                .satisfies(error -> assertThat(error.errorCode()).isEqualTo(expected));
        server.verify();
    }
}
