package com.payflow.auth.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.auth.content.HeroContent;
import com.payflow.auth.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.testcontainers.containers.PostgreSQLContainer;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "JWT_SECRET=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
    "PAYFLOW_INTERNAL_TOKEN=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
    "payflow.outbox.enabled=false", "PAYFLOW_ADMIN_EMAIL=", "PAYFLOW_ADMIN_PASSWORD=",
    "spring.datasource.hikari.maximum-pool-size=3", "spring.datasource.hikari.minimum-idle=0"
})
@AutoConfigureMockMvc
class HeroContentIT {
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    static { postgres.start(); }
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> postgres.getJdbcUrl() + "?currentSchema=auth");
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
        properties.add("spring.flyway.schemas", () -> "auth");
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> "auth");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    private static final String PUBLIC = "/api/v1/auth/site-content/hero";
    private static final String ADMIN = "/api/v1/auth/admin/site-content/hero";
    private org.springframework.test.web.servlet.request.RequestPostProcessor actor(String permission) {
        var principal = new AuthenticatedUser(UUID.randomUUID(), "test@example.com", null, Set.of(), Set.of(permission));
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority(permission))));
    }
    @Test void persistsPublishedContentAndRejectsStaleWritesAndUnauthorizedEdits() throws Exception {
        var seeded = mvc.perform(get(PUBLIC)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        HeroContent before = mapper.readValue(seeded, HeroContent.class);
        assertEquals("Accept payments.", before.title());
        mvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
        mvc.perform(get(ADMIN).with(actor("payment:read"))).andExpect(status().isForbidden());
        HeroContent edit = new HeroContent(before.version(), before.badge(), "Published headline", before.accent(),
                before.description(), before.supportingText(), before.primaryLabel(), before.secondaryLabel(), before.trustPoints());
        String payload = mapper.writeValueAsString(edit);
        mvc.perform(put(ADMIN).contentType("application/json").content(payload)).andExpect(status().isUnauthorized());
        mvc.perform(put(ADMIN).with(actor("payment:read")).contentType("application/json").content(payload)).andExpect(status().isForbidden());
        mvc.perform(put(ADMIN).with(actor("platform:admin")).contentType("application/json").content(payload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Published headline"))
                .andExpect(jsonPath("$.version").value(before.version() + 1));
        // A fresh anonymous request reads the committed database value.
        mvc.perform(get(PUBLIC)).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Published headline"));
        mvc.perform(put(ADMIN).with(actor("platform:admin")).contentType("application/json").content(payload)).andExpect(status().isConflict());
        mvc.perform(put(ADMIN).with(actor("platform:admin")).contentType("application/json")
                .content(payload.replace("Published headline", " "))).andExpect(status().isBadRequest());
        mvc.perform(put(PUBLIC).with(actor("platform:admin")).contentType("application/json").content(payload)).andExpect(status().isMethodNotAllowed());
    }
}
