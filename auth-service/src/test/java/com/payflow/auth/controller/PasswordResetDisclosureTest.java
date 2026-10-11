package com.payflow.auth.controller;

import com.payflow.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PasswordResetDisclosureTest {
    @Test
    void resetResponseNeverDisclosesTokenOrAccountExistence() throws Exception {
        AuthService service = mock(AuthService.class);
        when(service.initiatePasswordReset("existing@example.test")).thenReturn("secret-reset-token");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AuthController(service)).build();
        String existing = mvc.perform(post("/api/v1/auth/password-reset/initiate")
                .contentType("application/json").content("{\"email\":\"existing@example.test\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.resetToken").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/v1/auth/password-reset/initiate")
                .contentType("application/json").content("{\"email\":\"absent@example.test\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().string(existing))
                .andReturn().getResponse().getContentAsString();
        verify(service).initiatePasswordReset("existing@example.test");
        verify(service).initiatePasswordReset("absent@example.test");
    }
}
