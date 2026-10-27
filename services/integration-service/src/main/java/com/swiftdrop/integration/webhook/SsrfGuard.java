package com.swiftdrop.integration.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

@Component
public class SsrfGuard {

    private static final Logger log = LoggerFactory.getLogger(SsrfGuard.class);

    private final boolean allowHttp;
    private final boolean allowLocalAddresses;

    public SsrfGuard(
            @Value("${swiftdrop.webhook.allow-http:false}") boolean allowHttp,
            @Value("${swiftdrop.webhook.allow-local-addresses:false}") boolean allowLocalAddresses) {
        this.allowHttp = allowHttp;
        this.allowLocalAddresses = allowLocalAddresses;
    }

    /**
     * Validates a webhook destination URL against SSRF rules, performing DNS resolution.
     *
     * @param urlString destination URL string
     * @throws SsrfException if the URL or resolved IP violates security rules
     */
    public void validateUrl(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            throw new SsrfException("Webhook URL must not be empty");
        }

        URI uri;
        try {
            uri = URI.create(urlString.trim());
        } catch (IllegalArgumentException e) {
            throw new SsrfException("Malformed webhook destination URI: " + e.getMessage());
        }

        validateScheme(uri);
        resolveAndValidate(uri.getHost());
    }

    /**
     * Resolves the host at transmission time and validates that every returned address is safe.
     *
     * @param host destination host or IP
     * @return array of validated InetAddress records
     */
    public InetAddress[] resolveAndValidate(String host) {
        if (host == null || host.isBlank()) {
            throw new SsrfException("Webhook host must not be empty");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new SsrfException("Destination host cannot be resolved via DNS: " + host);
        }

        if (addresses == null || addresses.length == 0) {
            throw new SsrfException("No DNS address records found for host: " + host);
        }

        for (InetAddress address : addresses) {
            validateAddress(address);
        }

        return addresses;
    }

    private void validateScheme(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new SsrfException("Webhook URL must include a protocol scheme (https)");
        }
        scheme = scheme.toLowerCase();
        if ("https".equals(scheme)) {
            return;
        }
        if ("http".equals(scheme)) {
            if (allowHttp) {
                return;
            }
            throw new SsrfException("HTTP is disallowed for webhook delivery; HTTPS is required");
        }
        throw new SsrfException("Unsupported protocol scheme: " + scheme + ". Only HTTPS is permitted");
    }

    /**
     * Checks an individual IP address against loopback, private, link-local, and reserved ranges.
     */
    public void validateAddress(InetAddress address) {
        if (allowLocalAddresses) {
            return;
        }

        if (address.isLoopbackAddress()) {
            throw new SsrfException("Destination IP is in restricted loopback range (127.0.0.0/8 or ::1): " + address.getHostAddress());
        }

        if (address.isSiteLocalAddress()) {
            throw new SsrfException("Destination IP is in RFC 1918 private range (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16): " + address.getHostAddress());
        }

        if (address.isLinkLocalAddress()) {
            throw new SsrfException("Destination IP is in link-local range (169.254.0.0/16 or fe80::/10): " + address.getHostAddress());
        }

        if (address.isAnyLocalAddress()) {
            throw new SsrfException("Destination IP is wildcard address (0.0.0.0 or ::): " + address.getHostAddress());
        }

        if (address.isMulticastAddress()) {
            throw new SsrfException("Destination IP is in multicast range: " + address.getHostAddress());
        }

        byte[] bytes = address.getAddress();

        if (address instanceof Inet4Address) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;

            // Carrier-Grade NAT: 100.64.0.0/10 (100.64.0.0 - 100.127.255.255)
            if (b0 == 100 && (b1 >= 64 && b1 <= 127)) {
                throw new SsrfException("Destination IP is in carrier-grade NAT range (100.64.0.0/10): " + address.getHostAddress());
            }

            // Cloud metadata / link-local fallback: 169.254.169.254
            if (b0 == 169 && b1 == 254) {
                throw new SsrfException("Destination IP is in link-local / cloud metadata range: " + address.getHostAddress());
            }

            // Reserved / Class E: 240.0.0.0/4
            if (b0 >= 240) {
                throw new SsrfException("Destination IP is in reserved address space: " + address.getHostAddress());
            }

            // Current network: 0.0.0.0/8
            if (b0 == 0) {
                throw new SsrfException("Destination IP is in 0.0.0.0/8 network range: " + address.getHostAddress());
            }
        } else if (address instanceof Inet6Address) {
            // IPv4-mapped IPv6 address (::ffff:x.x.x.x)
            if (address.getHostAddress().startsWith("::ffff:") || (bytes.length == 16 && isIpv4Mapped(bytes))) {
                try {
                    byte[] ipv4Bytes = new byte[4];
                    System.arraycopy(bytes, 12, ipv4Bytes, 0, 4);
                    InetAddress ipv4 = InetAddress.getByAddress(ipv4Bytes);
                    validateAddress(ipv4);
                } catch (UnknownHostException e) {
                    throw new SsrfException("Invalid IPv4-mapped IPv6 address: " + address.getHostAddress());
                }
            }

            // Unique local address (fc00::/7)
            int firstByte = bytes[0] & 0xFF;
            if ((firstByte & 0xFE) == 0xFC) {
                throw new SsrfException("Destination IP is in IPv6 unique local range (fc00::/7): " + address.getHostAddress());
            }
        }
    }

    private boolean isIpv4Mapped(byte[] bytes) {
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) return false;
        }
        return (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF;
    }
}
