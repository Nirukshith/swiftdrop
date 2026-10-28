package com.swiftdrop.parcel.tracking;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Finds the client IP for rate limiting. X-Forwarded-For is trusted only when the request
 * comes from the gateway (a trusted proxy); anyone else could send a fake header.
 * The header is read from the right, skipping trusted proxies, because a client can put
 * anything on the left and only the entries added by our proxies are reliable.
 */
final class ClientIpResolver {

    static final String X_FORWARDED_FOR = "X-Forwarded-For";

    /** Literal IPv4 or IPv6 only, so InetAddress never does a DNS lookup on header input. */
    private static final String OCTET = "(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)";
    private static final Pattern IPV4 = Pattern.compile("^" + OCTET + "(\\." + OCTET + "){3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:.]*:[0-9a-fA-F:.]*$");

    private final List<IpAddressMatcher> trustedProxies;

    ClientIpResolver(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(IpAddressMatcher::new)
                .toList();
    }

    String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!isTrusted(remote)) {
            return remote;
        }
        List<String> hops = forwardedHops(request);
        for (int i = hops.size() - 1; i >= 0; i--) {
            String hop = hops.get(i);
            if (!isIpLiteral(hop)) {
                return remote; // written by a trusted proxy but unusable: fall back to the proxy address
            }
            if (!isTrusted(hop)) {
                return hop;
            }
        }
        return hops.isEmpty() ? remote : hops.get(0);
    }

    private static List<String> forwardedHops(HttpServletRequest request) {
        List<String> hops = new ArrayList<>();
        for (String header : Collections.list(request.getHeaders(X_FORWARDED_FOR))) {
            for (String part : header.split(",")) {
                String hop = part.trim();
                if (!hop.isEmpty()) {
                    hops.add(hop);
                }
            }
        }
        return hops;
    }

    private boolean isTrusted(String address) {
        if (!isIpLiteral(address)) {
            return false;
        }
        for (IpAddressMatcher matcher : trustedProxies) {
            try {
                if (matcher.matches(address)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // not a valid address for this matcher
            }
        }
        return false;
    }

    static boolean isIpLiteral(String value) {
        return value != null && value.length() <= 45
                && (IPV4.matcher(value).matches() || IPV6.matcher(value).matches());
    }
}
