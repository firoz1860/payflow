package com.payflow.ai.tools;

import com.payflow.common.security.PayFlowPrincipal;

import java.util.Map;

/**
 * Everything a tool is allowed to see about the caller and the request. The
 * authenticated {@link PayFlowPrincipal} is the ONLY source of identity and
 * tenancy — {@code merchantId} is never taken from arguments or model output.
 * {@code args} carries narrow, server-supplied parameters (e.g. a payment
 * reference); it never carries identity.
 */
public record ToolContext(PayFlowPrincipal principal, Map<String, String> args) {

    public ToolContext {
        args = args == null ? Map.of() : Map.copyOf(args);
    }

    public static ToolContext of(PayFlowPrincipal principal, Map<String, String> args) {
        return new ToolContext(principal, args);
    }

    public String arg(String name) {
        return args.get(name);
    }

    public boolean isAdmin() {
        return principal != null && principal.hasPermission("ai:admin");
    }
}
