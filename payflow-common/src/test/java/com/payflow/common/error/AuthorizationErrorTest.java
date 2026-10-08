package com.payflow.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthorizationErrorTest {
    @RestController
    static class ProtectedController {
        @GetMapping("/denied") String denied() { throw new AccessDeniedException("private authorization detail"); }
        @GetMapping("/unauthenticated") String unauthenticated() { throw new BadCredentialsException("private credential detail"); }
    }
    @Test void authorizationDenialIs403WithoutInternalDetails() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new ProtectedController()).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/denied")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Insufficient permissions"));
    }
    @Test void authenticationFailureIs401WithoutInternalDetails() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new ProtectedController()).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/unauthenticated")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }
}
