package com.payflow.auth.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;

@ConfigurationProperties("payflow.auth-email")
public class AuthEmailProperties {
    private boolean enabled;
    private String apiKey = "";
    private String from = "";
    private String frontendUrl = "";
    public void validate() {
        if (apiKey == null || !apiKey.matches("re_[A-Za-z0-9_-]{8,256}")
                || from == null || from.isBlank() || !from.contains("@")
                || from.contains("\r") || from.contains("\n") || from.length() > 255) {
            throw new IllegalStateException("Authentication email requires a provider key and sender");
        }
        try {
            URI origin = URI.create(frontendUrl);
            if (!"https".equalsIgnoreCase(origin.getScheme()) || origin.getHost() == null
                    || origin.getUserInfo() != null || origin.getQuery() != null || origin.getFragment() != null
                    || (origin.getPort() != -1 && origin.getPort() != 443)
                    || (origin.getPath() != null && !origin.getPath().isEmpty() && !origin.getPath().equals("/"))) {
                throw new IllegalArgumentException();
            }
        } catch (Exception ignored) {
            throw new IllegalStateException("Authentication email requires an HTTPS frontend origin");
        }
    }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String value) { apiKey = value; }
    public String getFrom() { return from; }
    public void setFrom(String value) { from = value; }
    public String getFrontendUrl() { return frontendUrl; }
    public void setFrontendUrl(String value) { frontendUrl = value; }
}
