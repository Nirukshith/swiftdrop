package com.swiftdrop.integration.webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SsrfGuardTest {

    private SsrfGuard ssrfGuard;

    @BeforeEach
    void setUp() {
        // Strict production configuration: allowHttp = false, allowLocalAddresses = false
        ssrfGuard = new SsrfGuard(false, false);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://example.com/webhook",
            "ftp://example.com/webhook",
            "file:///etc/passwd",
            "javascript:alert(1)",
            "gopher://evil.com"
    })
    @DisplayName("Reject non-HTTPS protocol schemes")
    void rejectNonHttpsSchemes(String url) {
        assertThatThrownBy(() -> ssrfGuard.validateUrl(url))
                .isInstanceOf(SsrfException.class);
    }

    @Test
    @DisplayName("Empty or null URLs are rejected")
    void rejectEmptyUrls() {
        assertThatThrownBy(() -> ssrfGuard.validateUrl(null))
                .isInstanceOf(SsrfException.class);
        assertThatThrownBy(() -> ssrfGuard.validateUrl("   "))
                .isInstanceOf(SsrfException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1",
            "127.0.1.1",
            "127.255.255.255",
            "::1"
    })
    @DisplayName("Reject loopback IP addresses")
    void rejectLoopbackAddresses(String ip) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(ip);
        assertThatThrownBy(() -> ssrfGuard.validateAddress(address))
                .isInstanceOf(SsrfException.class)
                .hasMessageContaining("loopback");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // 10.0.0.0/8
            "10.0.0.1",
            "10.255.255.254",
            // 172.16.0.0/12
            "172.16.0.1",
            "172.24.1.1",
            "172.31.255.254",
            // 192.168.0.0/16
            "192.168.0.1",
            "192.168.100.50"
    })
    @DisplayName("Reject RFC 1918 private IPv4 networks")
    void rejectPrivateIpv4Addresses(String ip) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(ip);
        assertThatThrownBy(() -> ssrfGuard.validateAddress(address))
                .isInstanceOf(SsrfException.class)
                .hasMessageContaining("private");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "169.254.169.254", // Cloud instance metadata
            "169.254.1.1",
            "fe80::1"          // IPv6 link-local
    })
    @DisplayName("Reject link-local and cloud metadata addresses")
    void rejectLinkLocalAddresses(String ip) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(ip);
        assertThatThrownBy(() -> ssrfGuard.validateAddress(address))
                .isInstanceOf(SsrfException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "100.64.0.1",
            "100.80.1.1",
            "100.127.255.254"
    })
    @DisplayName("Reject Carrier-Grade NAT (100.64.0.0/10) addresses")
    void rejectCarrierGradeNatAddresses(String ip) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(ip);
        assertThatThrownBy(() -> ssrfGuard.validateAddress(address))
                .isInstanceOf(SsrfException.class)
                .hasMessageContaining("carrier-grade NAT");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "224.0.0.1",
            "239.255.255.250"
    })
    @DisplayName("Reject multicast IPv4 addresses")
    void rejectMulticastAddresses(String ip) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(ip);
        assertThatThrownBy(() -> ssrfGuard.validateAddress(address))
                .isInstanceOf(SsrfException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0.0.0.0",
            "::"
    })
    @DisplayName("Reject wildcard / any-local addresses")
    void rejectAnyLocalAddresses(String ip) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(ip);
        assertThatThrownBy(() -> ssrfGuard.validateAddress(address))
                .isInstanceOf(SsrfException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "8.8.8.8",
            "1.1.1.1",
            "93.184.216.34",
            "2606:4700:4700::1111"
    })
    @DisplayName("Accept valid public IP addresses")
    void acceptPublicAddresses(String ip) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(ip);
        assertThatCode(() -> ssrfGuard.validateAddress(address))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Allow local addresses when explicitly configured in dev/test mode")
    void allowLocalAddressesWhenConfigured() throws UnknownHostException {
        SsrfGuard permissiveGuard = new SsrfGuard(true, true);
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        InetAddress privateIp = InetAddress.getByName("192.168.1.1");

        assertThatCode(() -> permissiveGuard.validateAddress(loopback)).doesNotThrowAnyException();
        assertThatCode(() -> permissiveGuard.validateAddress(privateIp)).doesNotThrowAnyException();
    }
}
