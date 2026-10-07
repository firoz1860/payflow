package com.payflow.auth.domain;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class PermissionTest {
    @Test
    void aiPermissionsExposeTheExpectedValues() {
        assertThat(Permission.AI_USE.value()).isEqualTo("ai:use");
        assertThat(Permission.AI_ADMIN.value()).isEqualTo("ai:admin");
    }
}
