package com.swiftdrop.integration.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookDispatcherTest {

    @Test
    void eachAttemptSignsItsOutgoingBodyWithANewTimestamp() throws Exception {
        SsrfGuard guard = mock(SsrfGuard.class);
        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(java.util.Map.of(), (a, b) -> true));
        when(response.body()).thenReturn("ok");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        MutableClock clock = new MutableClock(Instant.parse("2026-11-10T10:00:00Z"));
        WebhookDispatcher dispatcher = new WebhookDispatcher(guard, new ObjectMapper(), client, clock);
        String body = "{\"parcelId\":\"p-1\",\"status\":\"DELIVERED\"}";
        String secret = "merchant-secret-for-test";

        WebhookDispatcher.DispatchResult first = dispatcher.dispatch("https://example.com/hook", secret, "delivery-1", body);
        clock.advanceSeconds(1);
        WebhookDispatcher.DispatchResult second = dispatcher.dispatch("https://example.com/hook", secret, "delivery-1", body);

        assertThat(List.of(first, second)).allMatch(WebhookDispatcher.DispatchResult::success);
        assertThat(first.timestampSent()).isNotEqualTo(second.timestampSent());
        assertThat(first.signatureSent()).isNotEqualTo(second.signatureSent());
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(2)).send(requests.capture(), any(HttpResponse.BodyHandler.class));
        for (int i = 0; i < 2; i++) {
            WebhookDispatcher.DispatchResult result = List.of(first, second).get(i);
            HttpRequest request = requests.getAllValues().get(i);
            String sentBody = requestBody(request);
            assertThat(sentBody).isEqualTo(body);
            assertThat(result.requestHeaders()).containsEntry(WebhookSigner.TIMESTAMP_HEADER, result.timestampSent())
                    .containsEntry(WebhookSigner.SIGNATURE_HEADER, result.signatureSent())
                    .containsEntry(WebhookSigner.DELIVERY_ID_HEADER, "delivery-1");
            assertThat(request.headers().firstValue(WebhookSigner.TIMESTAMP_HEADER))
                    .contains(result.timestampSent());
            assertThat(request.headers().firstValue(WebhookSigner.SIGNATURE_HEADER))
                    .contains(result.signatureSent());
            assertThat(request.headers().firstValue(WebhookSigner.DELIVERY_ID_HEADER))
                    .contains("delivery-1");
            assertThat(WebhookSigner.verify(sentBody, result.signatureSent(), secret, 300,
                    Long.parseLong(result.timestampSent()))).isTrue();
            assertThat(WebhookSigner.verify(sentBody + " ", result.signatureSent(), secret, 300,
                    Long.parseLong(result.timestampSent()))).isFalse();
        }
    }

    @Test
    void transportErrorsDoNotExposeExceptionText() throws Exception {
        SsrfGuard guard = mock(SsrfGuard.class);
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("merchant-secret-for-test"))
                .thenThrow(new HttpTimeoutException("merchant-secret-for-test"));
        WebhookDispatcher dispatcher = new WebhookDispatcher(guard, new ObjectMapper(), client, Clock.systemUTC());

        for (int i = 0; i < 2; i++) {
            WebhookDispatcher.DispatchResult result = dispatcher.dispatch(
                    "https://example.com/hook", "merchant-secret-for-test", "delivery-1", "{}");
            assertThat(result.success()).isFalse();
            assertThat(result.statusCode()).isZero();
            assertThat(result.errorMessage()).doesNotContain("merchant-secret-for-test");
        }
    }

    private static String requestBody(HttpRequest request) throws InterruptedException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                body.writeBytes(bytes);
            }

            @Override
            public void onError(Throwable throwable) {
                failure.set(throwable);
                completed.countDown();
            }

            @Override
            public void onComplete() {
                completed.countDown();
            }
        });
        assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(failure.get()).isNull();
        return body.toString(StandardCharsets.UTF_8);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
