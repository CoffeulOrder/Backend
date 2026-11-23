package com.coffeul.payment.infrastructure;

import com.coffeul.common.error.BusinessException;
import com.coffeul.payment.api.PaymentErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PortOnePaymentCancellationClientTest {
    private static final String GET = "https://api.portone.io/payments/payment-test?storeId=store-test";
    private static final String POST = "https://api.portone.io/payments/payment-test/cancel";
    private static final String PAID = """
            {"status":"PAID","id":"payment-test","transactionId":"tx-test","storeId":"store-test",
             "channel":{"key":"channel-test","pgProvider":"INICIS_V2"},"version":"V2","currency":"KRW",
             "amount":{"total":4500,"cancelled":0}}
            """;
    private static final String CANCELLED = PAID.replace("PAID", "CANCELLED").replace("\"cancelled\":0", "\"cancelled\":4500");
    private static final PortOnePaymentClient.CancellationPayment PAYMENT =
            new PortOnePaymentClient.CancellationPayment("payment-test", "tx-test", "store-test", "channel-test", 4500);
    private PortOnePaymentClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        var builder = PortOnePaymentClient.configure(RestClient.builder(), "dummy-api-secret");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PortOnePaymentClient(builder.build());
    }

    @Test
    void fullRefundUsesStoredContextFullBalanceAndIdempotencyThenVerifiesGet() {
        lookup(PAID);
        server.expect(requestTo(POST)).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "PortOne dummy-api-secret"))
                .andExpect(header("Idempotency-Key", "\"persisted-key-12345\""))
                .andExpect(content().json("""
                        {"storeId":"store-test","reason":"고객 요청","requester":"CUSTOMER",
                         "amount":4500,"currentCancellableAmount":4500}
                        """))
                .andRespond(withSuccess(response("SUCCEEDED", 4500), MediaType.APPLICATION_JSON));
        lookup(CANCELLED);
        assertThat(client.cancelFullPayment(PAYMENT, "persisted-key-12345")).isTrue();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "invalid-key-with-quote-\"", "invalid-key-with-space 12345"})
    void invalidIdempotencyKeyNeverReachesProvider(String key) {
        assertThatThrownBy(() -> client.cancelFullPayment(PAYMENT, key))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.errorCode()).isEqualTo(PaymentErrorCode.PAYMENT_CONTEXT_MISMATCH));
        server.verify();
    }

    @Test
    void alreadyFullyCancelledRecoversWithoutSecondPost() {
        lookup(CANCELLED);
        assertThat(client.cancelFullPayment(PAYMENT, "persisted-key-12345")).isTrue();
        server.verify();
    }

    @Test
    void requestedResponseIsStillPending() {
        lookup(PAID);
        server.expect(requestTo(POST)).andRespond(withSuccess(response("REQUESTED", 4500), MediaType.APPLICATION_JSON));
        lookup(PAID);
        assertThat(client.cancelFullPayment(PAYMENT, "persisted-key-12345")).isFalse();
        server.verify();
    }

    @Test
    void existingAsyncRequestIsPolledWithoutPostingAgain() {
        lookup(PAID.replace("\"amount\":", "\"cancellations\":[{\"status\":\"REQUESTED\",\"totalAmount\":4500}],\"amount\":"));
        assertThat(client.cancelFullPayment(PAYMENT, "persisted-key-12345")).isFalse();
        server.verify();
    }

    @Test
    void postSucceededIsNotEnoughUntilFullCancelledAmountIsVisible() {
        lookup(PAID);
        server.expect(requestTo(POST)).andRespond(withSuccess(response("SUCCEEDED", 4500), MediaType.APPLICATION_JSON));
        lookup(PAID);
        assertThat(client.cancelFullPayment(PAYMENT, "persisted-key-12345")).isFalse();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"payment-test", "tx-test", "store-test", "channel-test", "INICIS_V2", "V2", "KRW"})
    void mismatchedContextStopsBeforeMoneyOperation(String field) {
        lookup(PAID.replace("\"" + field + "\"", "\"mismatch\""));
        fails(PaymentErrorCode.PAYMENT_CONTEXT_MISMATCH);
    }

    @Test
    void wrongOriginalAmountStopsBeforeMoneyOperation() {
        lookup(PAID.replace("4500", "4400"));
        fails(PaymentErrorCode.AMOUNT_MISMATCH);
    }

    @Test
    void partialCancellationCannotBecomeFullSuccess() {
        lookup(CANCELLED.replace("\"cancelled\":4500", "\"cancelled\":1000"));
        fails(PaymentErrorCode.AMOUNT_MISMATCH);
    }

    @Test
    void paidPaymentWithPriorPartialRefundCannotBeFullyRefundedAgain() {
        lookup(PAID.replace("\"cancelled\":0", "\"cancelled\":1000"));
        fails(PaymentErrorCode.AMOUNT_MISMATCH);
    }

    @Test
    void partialPostResponseIsNotFullSuccess() {
        lookup(PAID);
        server.expect(requestTo(POST)).andRespond(withSuccess(response("SUCCEEDED", 1000), MediaType.APPLICATION_JSON));
        fails(PaymentErrorCode.AMOUNT_MISMATCH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "UNKNOWN", "UNRECOGNIZED"})
    void unconfirmedResponseRemainsRetryable(String status) {
        lookup(PAID);
        server.expect(requestTo(POST)).andRespond(withSuccess(response(status, 4500), MediaType.APPLICATION_JSON));
        fails(PaymentErrorCode.LOOKUP_UNAVAILABLE);
    }

    @Test
    void timeoutThenRetryRecoversUsingGetWithoutAnotherPost() {
        lookup(PAID);
        server.expect(requestTo(POST)).andRespond(withException(new IOException("private PG response")));
        lookup(CANCELLED);
        assertThatThrownBy(() -> client.cancelFullPayment(PAYMENT, "persisted-key-12345"))
                .isInstanceOf(BusinessException.class).hasMessage(PaymentErrorCode.LOOKUP_UNAVAILABLE.message())
                .hasNoCause();
        assertThat(client.cancelFullPayment(PAYMENT, "persisted-key-12345")).isTrue();
        server.verify();
    }

    @Test
    void providerErrorDoesNotLeakBody() {
        lookup(PAID);
        server.expect(requestTo(POST)).andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                .body("sensitive upstream response"));
        fails(PaymentErrorCode.LOOKUP_UNAVAILABLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"4500\"", "4500.5", "-1"})
    void malformedCancelledAmountNeverConfirmsRefund(String amount) {
        lookup(CANCELLED.replace("\"cancelled\":4500", "\"cancelled\":" + amount));
        fails(PaymentErrorCode.LOOKUP_UNAVAILABLE);
    }

    @Test
    void incompleteStoredContextStopsBeforeAnyHttpCall() {
        var missingChannel = new PortOnePaymentClient.CancellationPayment(
                "payment-test", "tx-test", "store-test", null, 4500);
        assertThatThrownBy(() -> client.cancelFullPayment(missingChannel, "persisted-key-12345"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(PaymentErrorCode.PAYMENT_CONTEXT_MISMATCH));
        server.verify();
    }

    @Test
    void unexpectedPaymentStateCannotTriggerRefund() {
        lookup(PAID.replace("PAID", "READY"));
        fails(PaymentErrorCode.PAYMENT_CONTEXT_MISMATCH);
    }

    private void lookup(String body) {
        server.expect(requestTo(GET)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private String response(String status, int amount) {
        return "{\"cancellation\":{\"id\":\"cancel-test\",\"status\":\"" + status
                + "\",\"totalAmount\":" + amount + "}}";
    }

    private void fails(PaymentErrorCode expected) {
        assertThatThrownBy(() -> client.cancelFullPayment(PAYMENT, "persisted-key-12345"))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.errorCode()).isEqualTo(expected);
                    assertThat(error).hasNoCause();
                });
        server.verify();
    }
}
