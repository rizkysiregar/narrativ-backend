package com.rizkysiregar.feedzmore.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Resolves the caller IP used as rate limit key for anonymous requests.
 * <p>
 * The app runs behind Cloudflare Tunnel and ingress-nginx, so {@code getRemoteAddr()} is the proxy,
 * not the client. The IP is read from a configurable header ({@code app.rate-limit.client-ip-header}),
 * which must be one that the edge overwrites (Cloudflare always sets {@code CF-Connecting-IP}).
 * Do not point it to a header a client can append to unless a trusted proxy strips it first.
 * When the header is missing or is not a valid IP literal the socket address is used.
 */
@Component
public class ClientIpResolver {

    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})(\\.\\d{1,3}){3}$");
    private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9a-fA-F:.]{2,45}$");

    private final String headerName;

    public ClientIpResolver(@Value("${app.rate-limit.client-ip-header:CF-Connecting-IP}") String headerName) {
        this.headerName = headerName == null ? "" : headerName.trim();
    }

    public String resolve(HttpServletRequest request) {
        if (!headerName.isEmpty()) {
            String headerValue = request.getHeader(headerName);
            if (headerValue != null) {
                // "client, proxy1, proxy2": the first entry is the client
                String normalized = normalize(headerValue.split(",")[0].trim());
                if (normalized != null) {
                    return normalized;
                }
            }
        }
        String remoteAddr = request.getRemoteAddr();
        String normalized = normalize(remoteAddr);
        return normalized != null ? normalized : String.valueOf(remoteAddr);
    }

    // returns null when the value is not an IP literal, so garbage can never become a Redis key
    private String normalize(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        boolean ipv4 = IPV4.matcher(value).matches();
        boolean ipv6 = !ipv4 && value.indexOf(':') >= 0 && IPV6_CHARS.matcher(value).matches();
        if (!ipv4 && !ipv6) {
            return null; // never hand a hostname to InetAddress, it would trigger a DNS lookup
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            if (address instanceof Inet4Address) {
                return address.getHostAddress();
            }
            if (address instanceof Inet6Address) {
                // a single subscriber usually owns a whole /64, so limiting per full address is trivial to dodge
                byte[] raw = address.getAddress();
                return String.format("%02x%02x:%02x%02x:%02x%02x:%02x%02x::/64",
                        raw[0], raw[1], raw[2], raw[3], raw[4], raw[5], raw[6], raw[7]);
            }
        } catch (UnknownHostException e) {
            return null;
        }
        return null;
    }
}
