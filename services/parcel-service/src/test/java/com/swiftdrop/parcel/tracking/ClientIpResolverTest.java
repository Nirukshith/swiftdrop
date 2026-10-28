package com.swiftdrop.parcel.tracking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.5/32", "172.16.0.0/12", "::1/128"));

    private static MockHttpServletRequest request(String remoteAddr, String... forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        for (String value : forwardedFor) {
            request.addHeader(ClientIpResolver.X_FORWARDED_FOR, value);
        }
        return request;
    }

    @Test
    @DisplayName("Direct callers are identified by their socket address; their X-Forwarded-For is ignored")
    void untrustedCallerCannotSpoof() {
        assertThat(resolver.resolve(request("203.0.113.9"))).isEqualTo("203.0.113.9");
        assertThat(resolver.resolve(request("203.0.113.9", "198.51.100.1"))).isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("From the gateway, the client is the last X-Forwarded-For hop")
    void trustedGateway() {
        assertThat(resolver.resolve(request("10.0.0.5", "198.51.100.1"))).isEqualTo("198.51.100.1");
        assertThat(resolver.resolve(request("0:0:0:0:0:0:0:1", "2001:db8::7"))).isEqualTo("2001:db8::7");
    }

    @Test
    @DisplayName("Fake entries a client puts on the left are skipped; trusted proxies on the right too")
    void readsFromTheRight() {
        assertThat(resolver.resolve(request("10.0.0.5", "1.2.3.4, 198.51.100.1"))).isEqualTo("198.51.100.1");
        assertThat(resolver.resolve(request("10.0.0.5", "1.2.3.4, 198.51.100.1, 172.18.0.3"))).isEqualTo("198.51.100.1");
        assertThat(resolver.resolve(request("10.0.0.5", "1.2.3.4", "198.51.100.1"))).isEqualTo("198.51.100.1");
    }

    @Test
    @DisplayName("Gateway without the header, unusable header, or only proxy hops")
    void edgeCases() {
        assertThat(resolver.resolve(request("10.0.0.5"))).isEqualTo("10.0.0.5");
        assertThat(resolver.resolve(request("10.0.0.5", "unknown"))).isEqualTo("10.0.0.5");
        assertThat(resolver.resolve(request("10.0.0.5", "999.1.1.1"))).isEqualTo("10.0.0.5");
        assertThat(resolver.resolve(request("10.0.0.5", "172.20.0.2, 172.20.0.3"))).isEqualTo("172.20.0.2");
    }

    @Test
    @DisplayName("Only literal IP addresses are accepted, so no DNS lookups happen")
    void ipLiterals() {
        assertThat(ClientIpResolver.isIpLiteral("192.168.1.10")).isTrue();
        assertThat(ClientIpResolver.isIpLiteral("2001:db8::1")).isTrue();
        assertThat(ClientIpResolver.isIpLiteral("256.1.1.1")).isFalse();
        assertThat(ClientIpResolver.isIpLiteral("example.com")).isFalse();
        assertThat(ClientIpResolver.isIpLiteral("")).isFalse();
        assertThat(ClientIpResolver.isIpLiteral(null)).isFalse();
    }
}
