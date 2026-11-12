package com.swiftdrop.operations.pricing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link InternalPricingServiceFilter}.
 *
 * Verifies that:
 * - Valid internal pricing token establishes SCOPE_pricing:read + ROLE_INTERNAL_SERVICE context.
 * - Invalid or missing token falls through to the next filter (JWT chain handles it).
 * - Non-pricing paths are skipped entirely (shouldNotFilter).
 *
 * Covers Issue #220 regression: internal services must receive 200, not 401,
 * when presenting the shared pricing token.
 *
 * Uses plain lambdas instead of Mockito mocks to avoid the macOS JVM Mockito
 * InlineByteBuddy initialization issue that affects the operations-service test suite.
 */
class InternalPricingServiceFilterTest {

    private static final String VALID_TOKEN = "swiftdrop-internal-pricing-secret-2026";
    private static final String INVALID_TOKEN = "wrong-token";

    private InternalPricingServiceFilter filter;

    @BeforeEach
    void setUp() {
        filter = new InternalPricingServiceFilter(VALID_TOKEN);
        SecurityContextHolder.clearContext();
    }

    // ----------------------------------------------------------------
    // shouldNotFilter
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("shouldNotFilter")
    class ShouldNotFilter {

        @Test
        @DisplayName("skips non-pricing paths")
        void skipsNonPricingPaths() {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/parcels");
            assertThat(filter.shouldNotFilter(request)).isTrue();
        }

        @Test
        @DisplayName("does not skip /internal/v1/pricing/quote")
        void doesNotSkipPricingPath() {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/pricing/quote");
            assertThat(filter.shouldNotFilter(request)).isFalse();
        }

        @Test
        @DisplayName("does not skip /internal/v1/pricing root")
        void doesNotSkipPricingRoot() {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/pricing");
            assertThat(filter.shouldNotFilter(request)).isFalse();
        }
    }

    // ----------------------------------------------------------------
    // doFilterInternal — valid token
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("valid internal token")
    class ValidToken {

        @Test
        @DisplayName("security context contains SCOPE_pricing:read and ROLE_INTERNAL_SERVICE during chain")
        void securityContextContainsCorrectAuthoritiesDuringChain() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/pricing/quote");
            request.addHeader(InternalPricingServiceFilter.INTERNAL_TOKEN_HEADER, VALID_TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();

            AtomicReference<Authentication> captured = new AtomicReference<>();
            FilterChain capturingChain = (req, res) ->
                    captured.set(SecurityContextHolder.getContext().getAuthentication());

            filter.doFilterInternal(request, response, capturingChain);

            assertThat(captured.get()).isNotNull();
            assertThat(captured.get().getAuthorities())
                    .extracting(a -> a.getAuthority())
                    .contains("SCOPE_pricing:read", "ROLE_INTERNAL_SERVICE");
            assertThat(captured.get().getName()).isEqualTo("internal-pricing-caller");
        }

        @Test
        @DisplayName("clears security context after filter chain completes")
        void clearsSecurityContextAfterChain() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/pricing/quote");
            request.addHeader(InternalPricingServiceFilter.INTERNAL_TOKEN_HEADER, VALID_TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, (req, res) -> { /* no-op */ });

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("chain is invoked for valid token")
        void chainIsInvokedForValidToken() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/pricing/quote");
            request.addHeader(InternalPricingServiceFilter.INTERNAL_TOKEN_HEADER, VALID_TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();

            AtomicBoolean chainCalled = new AtomicBoolean(false);
            filter.doFilterInternal(request, response, (req, res) -> chainCalled.set(true));

            assertThat(chainCalled.get()).isTrue();
        }
    }

    // ----------------------------------------------------------------
    // doFilterInternal — invalid / missing token
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("invalid or missing token")
    class InvalidOrMissingToken {

        @Test
        @DisplayName("falls through to next filter when token is missing (no synthetic auth set)")
        void fallthroughWhenMissing() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/pricing/quote");
            MockHttpServletResponse response = new MockHttpServletResponse();

            AtomicBoolean chainCalled = new AtomicBoolean(false);
            AtomicReference<Authentication> authDuringChain = new AtomicReference<>();
            FilterChain chain = (req, res) -> {
                chainCalled.set(true);
                authDuringChain.set(SecurityContextHolder.getContext().getAuthentication());
            };

            filter.doFilterInternal(request, response, chain);

            // Filter delegates to chain (JWT resource-server chain will reject without a valid JWT)
            assertThat(chainCalled.get()).isTrue();
            // No synthetic pricing auth set
            assertThat(authDuringChain.get()).isNull();
        }

        @Test
        @DisplayName("falls through to next filter when token is incorrect")
        void fallthroughWhenWrongToken() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/pricing/quote");
            request.addHeader(InternalPricingServiceFilter.INTERNAL_TOKEN_HEADER, INVALID_TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();

            AtomicBoolean chainCalled = new AtomicBoolean(false);
            AtomicReference<Authentication> authDuringChain = new AtomicReference<>();
            FilterChain chain = (req, res) -> {
                chainCalled.set(true);
                authDuringChain.set(SecurityContextHolder.getContext().getAuthentication());
            };

            filter.doFilterInternal(request, response, chain);

            assertThat(chainCalled.get()).isTrue();
            assertThat(authDuringChain.get()).isNull();
        }
    }
}
