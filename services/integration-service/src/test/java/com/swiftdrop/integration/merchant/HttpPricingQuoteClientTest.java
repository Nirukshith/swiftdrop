package com.swiftdrop.integration.merchant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

class HttpPricingQuoteClientTest {
    private MockRestServiceServer server;
    private HttpPricingQuoteClient client;
    private final PricingAccessToken tokens = mock(PricingAccessToken.class);
    private final StructuredAddress address = new StructuredAddress("10 Main Road", null, "Colombo", "Colombo", "00100");
    private final CreateMerchantOrderRequest order = new CreateMerchantOrderRequest("order", "Recipient", "+94771234567",
            address, address, MerchantParcelSize.SMALL, new BigDecimal("1.5"));
    private static final String URL = "http://pricing/internal/v1/pricing/quote?pickupDistrict=Colombo&dropoffDistrict=Colombo&weightKg=1.5&size=SMALL";

    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("http://pricing");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpPricingQuoteClient(builder.build(), tokens);
        when(tokens.get()).thenReturn("dedicated-service-token");
    }

    @Test void sendsServiceBearerAndUsesTheMergedQuoteResponse() {
        server.expect(requestTo(URL)).andExpect(header("Authorization", "Bearer dedicated-service-token"))
                .andRespond(withSuccess("{\"currency\":\"LKR\",\"totalFee\":850}", MediaType.APPLICATION_JSON));
        assertThat(client.quote(order)).isEqualByComparingTo("850");
        server.verify();
    }

    @Test void unauthorizedInvalidatesTokenWithoutAutomaticRetry() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> client.quote(order)).isInstanceOf(HttpClientErrorException.Unauthorized.class);
        verify(tokens).invalidate("dedicated-service-token");
        server.verify();
    }

    @Test void deniedScopeDoesNotRefreshOrRetry() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> client.quote(order)).isInstanceOf(HttpClientErrorException.Forbidden.class);
        verify(tokens, never()).invalidate(any());
        server.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"currency\":\"USD\",\"totalFee\":850}", "{\"currency\":\"LKR\",\"totalFee\":-1}"})
    void rejectsIncompleteOrInvalidQuotes(String response) {
        server.expect(requestTo(URL)).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.quote(order)).isInstanceOf(IllegalStateException.class);
        server.verify();
    }
}
