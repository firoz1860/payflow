package com.payflow.ledger.integration;

import com.payflow.common.security.PayFlowPrincipal;
import com.payflow.ledger.domain.LedgerPosting;
import com.payflow.ledger.repository.LedgerRepositories.LedgerEntryRepository;
import com.payflow.ledger.repository.LedgerRepositories.LedgerPostingRepository;
import com.payflow.ledger.service.LedgerService;
import com.payflow.ledger.service.PaymentPostingBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class MerchantPostingReadIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired LedgerService ledger;
    @Autowired PaymentPostingBuilder builder;
    @Autowired LedgerPostingRepository postings;
    @Autowired LedgerEntryRepository entries;

    private RequestPostProcessor actor(UUID merchantId, String permission) {
        var principal = PayFlowPrincipal.forUser(UUID.randomUUID(), merchantId, Set.of(permission));
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority(permission))));
    }

    private LedgerPosting capture(UUID merchantId) {
        return ledger.post(builder.capture("test_" + UUID.randomUUID(), merchantId.toString(),
                "INR", BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test void readsAnOwnedPostingOutsideTheRecentWindowWithoutWriting() throws Exception {
        UUID merchantId = UUID.randomUUID();
        LedgerPosting historical = capture(merchantId);
        for (int i = 0; i < 201; i++) capture(merchantId);
        String recent = mvc.perform(get("/api/v1/ledger/postings").param("limit", "200")
                        .with(actor(merchantId, "ledger:read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(200))
                .andReturn().getResponse().getContentAsString();
        assertThat(recent).doesNotContain(historical.getId().toString());
        long postingCount = postings.count();
        long entryCount = entries.count();
        String path = "/api/v1/ledger/postings/" + historical.getId();
        mvc.perform(get(path).with(actor(merchantId, "ledger:read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(historical.getId().toString()))
                .andExpect(jsonPath("$.sourceId").value(historical.getSourceId()))
                .andExpect(jsonPath("$.totalDebit").value(10)).andExpect(jsonPath("$.totalCredit").value(10));
        mvc.perform(get(path + "/entries").with(actor(merchantId, "ledger:read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        assertThat(postings.count()).isEqualTo(postingCount);
        assertThat(entries.count()).isEqualTo(entryCount);
    }

    @Test void anotherMerchantAndAnUnknownPostingBothReturn404() throws Exception {
        UUID owner = UUID.randomUUID();
        LedgerPosting posting = capture(owner);
        String path = "/api/v1/ledger/postings/" + posting.getId();
        for (String suffix : List.of("", "/entries")) {
            mvc.perform(get(path + suffix).with(actor(UUID.randomUUID(), "ledger:read")))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/ledger/postings/" + UUID.randomUUID() + suffix)
                            .with(actor(owner, "ledger:read"))).andExpect(status().isNotFound());
        }
    }

    @Test void readsRequireAuthenticationPermissionAndMerchantContext() throws Exception {
        UUID owner = UUID.randomUUID();
        String path = "/api/v1/ledger/postings/" + capture(owner).getId();
        for (String suffix : List.of("", "/entries")) {
            mvc.perform(get(path + suffix)).andExpect(status().isUnauthorized());
            mvc.perform(get(path + suffix).with(actor(owner, "payments:read"))).andExpect(status().isForbidden());
            mvc.perform(get(path + suffix).with(actor(null, "ledger:read"))).andExpect(status().isForbidden());
        }
    }

    @Test void malformedPostingIdentifiersAreRejected() throws Exception {
        for (String suffix : List.of("", "/entries")) {
            mvc.perform(get("/api/v1/ledger/postings/not-a-uuid" + suffix)
                            .with(actor(UUID.randomUUID(), "ledger:read")))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test void missingEndpointsReturn404WithoutEchoingThePath() throws Exception {
        mvc.perform(get("/api/v1/ledger/invalid-endpoint").with(actor(UUID.randomUUID(), "ledger:read")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The requested endpoint does not exist"));
    }
}
