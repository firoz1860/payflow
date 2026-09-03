package com.payflow.ledger.domain;

import java.sql.Types;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.PersistentClass;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaMappingTest {

    @Test
    void monetaryCurrencyColumnsUseTheCharJdbcType() {
        PersistentClass account = mappingFor(LedgerAccount.class);
        PersistentClass posting = mappingFor(LedgerPosting.class);
        PersistentClass entry = mappingFor(LedgerEntry.class);

        assertThat(jdbcType(account, "currency")).isEqualTo(Types.CHAR);
        assertThat(jdbcType(posting, "currency")).isEqualTo(Types.CHAR);
        assertThat(jdbcType(entry, "currency")).isEqualTo(Types.CHAR);
    }

    private PersistentClass mappingFor(Class<?> entity) {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.DIALECT, PostgreSQLDialect.class)
                .build();
        try {
            return new MetadataSources(registry).addAnnotatedClass(entity)
                    .buildMetadata().getEntityBinding(entity.getName());
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private int jdbcType(PersistentClass entity, String property) {
        return ((BasicValue) entity.getProperty(property).getValue())
                .getResolution().getJdbcType().getJdbcTypeCode();
    }
}
