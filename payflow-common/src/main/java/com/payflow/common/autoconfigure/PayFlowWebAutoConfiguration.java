package com.payflow.common.autoconfigure;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.client.ServiceClientFactory;
import com.payflow.common.error.GlobalExceptionHandler;
import com.payflow.common.security.InternalAuthFilter;
import com.payflow.common.web.CorrelationIdFilter;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import java.time.Duration;
@AutoConfiguration
@ConditionalOnClass(Filter.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PayFlowWebAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler payflowGlobalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
        FilterRegistrationBean<CorrelationIdFilter> registration =
                new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
    @Bean
    @ConditionalOnProperty(prefix = "payflow.internal", name = "token")
    public FilterRegistrationBean<InternalAuthFilter> internalAuthFilterRegistration(
            @org.springframework.beans.factory.annotation.Value("${payflow.internal.token}") String token,
            ObjectMapper objectMapper) {
        FilterRegistrationBean<InternalAuthFilter> registration =
                new FilterRegistrationBean<>(new InternalAuthFilter(token, objectMapper));
        registration.addUrlPatterns("/internal/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "payflow.internal", name = "token")
    public ServiceClientFactory serviceClientFactory(
            @org.springframework.beans.factory.annotation.Value("${payflow.internal.token}") String token,
            @org.springframework.beans.factory.annotation.Value("${payflow.internal.response-timeout-ms:3000}") long responseTimeoutMs,
            @org.springframework.beans.factory.annotation.Value("${payflow.internal.connect-timeout-ms:1000}") long connectTimeoutMs) {
        return new ServiceClientFactory(token,
                Duration.ofMillis(responseTimeoutMs), Duration.ofMillis(connectTimeoutMs));
    }
}
