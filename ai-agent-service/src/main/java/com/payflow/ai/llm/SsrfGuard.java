package com.payflow.ai.llm;

import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * Validates a user-supplied base URL for {@link AiProvider#CUSTOM_OPENAI_COMPATIBLE}
 * before it is ever used as an outbound target. This closes the SSRF hole a BYOK
 * custom endpoint would otherwise open: an attacker could point the "LLM" at an
 * internal service or the cloud metadata endpoint and exfiltrate credentials.
 *
 * <p>The guard requires HTTPS, resolves the host, and rejects the request if ANY
 * resolved address is loopback, any-local, link-local, site-local (RFC1918),
 * unique-local IPv6, or the well-known cloud metadata addresses/hosts.
 */
@Component
public class SsrfGuard {

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost",
            "metadata.google.internal",
            "metadata",
            "169.254.169.254");

    public void validateBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            throw PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED, "A base URL is required for a custom provider");
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException ex) {
            throw PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED, "Invalid base URL");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !scheme.equalsIgnoreCase("https")) {
            throw PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED, "Custom provider base URL must use https");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED, "Custom provider base URL must include a host");
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (BLOCKED_HOSTS.contains(normalizedHost)) {
            throw PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED, "Custom provider base URL targets a disallowed host");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException ex) {
            throw PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED, "Custom provider host could not be resolved");
        }

        for (InetAddress address : addresses) {
            if (isDisallowed(address)) {
                throw PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Custom provider base URL resolves to a disallowed (internal) address");
            }
        }
    }

    private boolean isDisallowed(InetAddress address) {
        if (address.isLoopbackAddress()
                || address.isAnyLocalAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        // IPv6 unique-local range fc00::/7 (not covered by isSiteLocalAddress for IPv6).
        if (bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC) {
            return true;
        }
        // IPv4 cloud metadata 169.254.169.254 is link-local (already blocked); keep an
        // explicit belt-and-braces check in case of mapped representations.
        return address.getHostAddress().equals("169.254.169.254");
    }
}
