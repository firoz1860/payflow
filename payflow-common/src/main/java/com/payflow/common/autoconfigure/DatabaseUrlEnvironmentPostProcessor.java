package com.payflow.common.autoconfigure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts a platform-style DATABASE_URL such as
 * postgresql://user:password@host:5432/database into Spring JDBC properties.
 *
 * Local Docker development remains unchanged because this processor is inert
 * unless DATABASE_URL is present.
 */
public final class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String SOURCE_NAME = "payflowDatabaseUrl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String raw = environment.getProperty("DATABASE_URL");
        if (raw == null || raw.isBlank()) {
            return;
        }

        URI uri;
        try {
            uri = URI.create(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("DATABASE_URL is not a valid URI", ex);
        }

        String scheme = uri.getScheme();
        if (!"postgres".equalsIgnoreCase(scheme) && !"postgresql".equalsIgnoreCase(scheme)) {
            throw new IllegalStateException("DATABASE_URL must use postgres:// or postgresql://");
        }
        if (uri.getHost() == null || uri.getPath() == null || uri.getPath().length() <= 1) {
            throw new IllegalStateException("DATABASE_URL must include host and database name");
        }

        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        String database = uri.getPath().substring(1);
        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://")
                .append(uri.getHost()).append(':').append(port).append('/').append(database);
        String schema = environment.getProperty("PAYFLOW_DB_SCHEMA");
        String query = uri.getRawQuery();
        if (query != null && !query.isBlank()) {
            jdbc.append('?').append(query);
        }
        if (schema != null && !schema.isBlank()) {
            jdbc.append(query == null || query.isBlank() ? '?' : '&')
                    .append("currentSchema=").append(schema.trim());
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", jdbc.toString());
        if (schema != null && !schema.isBlank()) {
            String normalizedSchema = schema.trim();
            properties.put("spring.flyway.schemas", normalizedSchema);
            properties.put("spring.flyway.default-schema", normalizedSchema);
            properties.put("spring.flyway.create-schemas", "true");
            properties.put("spring.jpa.properties.hibernate.default_schema", normalizedSchema);
        }

        String userInfo = uri.getRawUserInfo();
        if (userInfo != null && !userInfo.isBlank()) {
            int separator = userInfo.indexOf(':');
            String rawUser = separator >= 0 ? userInfo.substring(0, separator) : userInfo;
            String rawPassword = separator >= 0 ? userInfo.substring(separator + 1) : "";
            properties.put("spring.datasource.username", decode(rawUser));
            properties.put("spring.datasource.password", decode(rawPassword));
        }

        environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, properties));
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
