package com.swiftdrop.parcel.tracking;

import com.swiftdrop.parcel.parcel.PricingQuoteClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Public tracking rate limit as it behaves behind the gateway (docs/tracking-design.md section 6).
 *
 * The gateway is configured as a trusted proxy here (172.16.0.0/12, Docker's default bridge
 * range), as Compose would set it. With X-Forwarded-For from the gateway every visitor gets
 * their own limit; without it, every visitor is counted as the gateway itself and they all share
 * one limit, which is why the gateway must send the header (see the Week 7 verification doc).
 */
@SpringBootTest(properties = {
        "swiftdrop.tracking.rate-limit.burst=3",
        "swiftdrop.tracking.rate-limit.requests-per-minute=1",
        "swiftdrop.tracking.rate-limit.trusted-proxies=172.16.0.0/12",
})
@AutoConfigureMockMvc
@Testcontainers
class TrackingRateLimitGatewayIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final int BURST = 3;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PricingQuoteClient pricingQuoteClient;

    private static MockHttpServletRequestBuilder lookup(String path, String remoteAddress, String forwardedFor) {
        MockHttpServletRequestBuilder request = get(path).with(r -> {
            r.setRemoteAddr(remoteAddress);
            return r;
        });
        return forwardedFor == null ? request : request.header("X-Forwarded-For", forwardedFor);
    }

    private static String unknownNumber() {
        return "/api/v1/tracking/SD-20991231-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private int statusOf(String remoteAddress, String forwardedFor) throws Exception {
        return mockMvc.perform(lookup(unknownNumber(), remoteAddress, forwardedFor)).andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("Through a gateway that sends X-Forwarded-For, each visitor has their own limit")
    void eachVisitorHasOwnLimitBehindGateway() throws Exception {
        String gateway = "172.20.0.11";
        String first = "198.51.100.10";
        String second = "198.51.100.20";

        for (int i = 0; i < BURST; i++) {
            assertThat(statusOf(gateway, first)).as("first visitor, request %d", i + 1).isEqualTo(404);
        }
        assertThat(statusOf(gateway, first)).as("first visitor over the limit").isEqualTo(429);

        assertThat(statusOf(gateway, second)).as("second visitor is not affected").isEqualTo(404);
    }

    @Test
    @DisplayName("Through a gateway that sends no X-Forwarded-For, all visitors share the gateway's limit")
    void withoutForwardedForAllVisitorsShareOneLimit() throws Exception {
        String gateway = "172.20.0.12";

        for (int i = 0; i < BURST; i++) {
            assertThat(statusOf(gateway, null)).isEqualTo(404);
        }
        // Any further visitor coming through the same gateway is now refused as well.
        assertThat(statusOf(gateway, null)).isEqualTo(429);
    }

    @Test
    @DisplayName("A caller that is not the gateway cannot dodge the limit with a fake X-Forwarded-For")
    void untrustedCallerCannotSpoofForwardedFor() throws Exception {
        String direct = "198.51.100.30";

        for (int i = 0; i < BURST; i++) {
            assertThat(statusOf(direct, "203.0.113." + i)).isEqualTo(404);
        }
        assertThat(statusOf(direct, "203.0.113.99")).as("new fake address, same real caller").isEqualTo(429);
    }

    @Test
    @DisplayName("Fake entries a visitor adds in front are ignored; the gateway's own entry identifies them")
    void leftSideEntriesCannotBeForged() throws Exception {
        String gateway = "172.20.0.13";
        String real = "198.51.100.40";

        for (int i = 0; i < BURST; i++) {
            // nginx $proxy_add_x_forwarded_for appends the real address after whatever the client sent
            assertThat(statusOf(gateway, "10.9.9." + i + ", " + real)).isEqualTo(404);
        }
        assertThat(statusOf(gateway, "10.9.9.200, " + real)).isEqualTo(429);
    }

    @Test
    @DisplayName("Over the limit: 429 with Retry-After and the shared error body, on the lookup and on the stream")
    void tooManyRequestsResponse() throws Exception {
        String gateway = "172.20.0.14";
        String client = "198.51.100.50";
        for (int i = 0; i < BURST; i++) {
            statusOf(gateway, client);
        }

        MvcResult lookup = mockMvc.perform(lookup(unknownNumber(), gateway, client))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code", is("TOO_MANY_REQUESTS")))
                .andExpect(jsonPath("$.message", is("Too many tracking requests, please retry later")))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andReturn();
        assertThat(Long.parseLong(lookup.getResponse().getHeader("Retry-After"))).isPositive();

        mockMvc.perform(lookup(unknownNumber() + "/stream", gateway, client).header("Accept", "text/event-stream"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code", is("TOO_MANY_REQUESTS")));
    }
}
