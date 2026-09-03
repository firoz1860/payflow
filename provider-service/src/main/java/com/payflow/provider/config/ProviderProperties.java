package com.payflow.provider.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix = "payflow.provider")
public class ProviderProperties {
    private String defaultProvider = "sandbox";
    private Razorpay razorpay = new Razorpay();
    private Stripe stripe = new Stripe();
    private Sandbox sandbox = new Sandbox();
    public static class Razorpay {
        private String keyId;
        private String keySecret;
        private String webhookSecret;
        private String checkoutUrl = "https://checkout.razorpay.com/v1/checkout.js";
        public String getKeyId() {
            return keyId;
        }
        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }
        public String getKeySecret() {
            return keySecret;
        }
        public void setKeySecret(String keySecret) {
            this.keySecret = keySecret;
        }
        public String getWebhookSecret() {
            return webhookSecret;
        }
        public void setWebhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
        }
        public String getCheckoutUrl() {
            return checkoutUrl;
        }
        public void setCheckoutUrl(String checkoutUrl) {
            this.checkoutUrl = checkoutUrl;
        }
    }
    public static class Stripe {
        private String secretKey;
        private String webhookSecret;
        public String getSecretKey() {
            return secretKey;
        }
        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }
        public String getWebhookSecret() {
            return webhookSecret;
        }
        public void setWebhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
        }
    }
    public static class Sandbox {
        private String webhookSecret = "sandbox-webhook-secret";
        private String checkoutUrl = "http://localhost:8086/sandbox/checkout";
        // Receiver identity baked into sandbox UPI QR payloads.
        private String upiVpa = "payflow.sandbox@upi";
        private String payeeName = "PayFlow Sandbox";
        public String getWebhookSecret() {
            return webhookSecret;
        }
        public void setWebhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
        }
        public String getCheckoutUrl() {
            return checkoutUrl;
        }
        public void setCheckoutUrl(String checkoutUrl) {
            this.checkoutUrl = checkoutUrl;
        }
        public String getUpiVpa() {
            return upiVpa;
        }
        public void setUpiVpa(String upiVpa) {
            this.upiVpa = upiVpa;
        }
        public String getPayeeName() {
            return payeeName;
        }
        public void setPayeeName(String payeeName) {
            this.payeeName = payeeName;
        }
    }
    public String getDefaultProvider() {
        return defaultProvider;
    }
    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }
    public Razorpay getRazorpay() {
        return razorpay;
    }
    public void setRazorpay(Razorpay razorpay) {
        this.razorpay = razorpay;
    }
    public Stripe getStripe() {
        return stripe;
    }
    public void setStripe(Stripe stripe) {
        this.stripe = stripe;
    }
    public Sandbox getSandbox() {
        return sandbox;
    }
    public void setSandbox(Sandbox sandbox) {
        this.sandbox = sandbox;
    }
}
