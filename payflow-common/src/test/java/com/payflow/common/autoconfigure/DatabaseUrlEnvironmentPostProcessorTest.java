package com.payflow.common.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseUrlEnvironmentPostProcessorTest {

    @Test
    void convertsRenderDatabaseUrlAndAppliesServiceSchema() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql://payflow:p%40ss@db.internal:5432/payflow")
                .withProperty("PAYFLOW_DB_SCHEMA", "payment");

        new DatabaseUrlEnvironmentPostProcessor()
                .postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://db.internal:5432/payflow?currentSchema=payment");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("payflow");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("p@ss");
        assertThat(environment.getProperty("spring.flyway.schemas")).isEqualTo("payment");
        assertThat(environment.getProperty("spring.jpa.properties.hibernate.default_schema"))
                .isEqualTo("payment");
    }

    @Test
    void leavesLocalConfigurationUntouchedWithoutDatabaseUrl() {
        MockEnvironment environment = new MockEnvironment();

        new DatabaseUrlEnvironmentPostProcessor()
                .postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.url")).isNull();
    }
}
