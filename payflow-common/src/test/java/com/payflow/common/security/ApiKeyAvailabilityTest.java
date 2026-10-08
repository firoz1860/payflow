package com.payflow.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class ApiKeyAvailabilityTest {
    @Test void unavailableVerifierReturns503WithoutRunningProtectedHandler() throws Exception {
        var filter=new ApiKeyAuthenticationFilter(key -> {throw new ApiKeyVerifier.UnavailableException(new RuntimeException("private dependency detail"));},new ObjectMapper().findAndRegisterModules(),false);
        var request=new MockHttpServletRequest();request.addHeader("Authorization","Bearer sk_test_example");
        var response=new MockHttpServletResponse();var called=new AtomicBoolean();
        assertDoesNotThrow(() -> filter.doFilter(request,response,(req,res)->called.set(true)));
        assertEquals(503,response.getStatus());assertFalse(called.get());
        assertFalse(response.getContentAsString().contains("private dependency detail"));
    }
    @Test void invalidKeyStillReturns401() throws Exception {
        var filter=new ApiKeyAuthenticationFilter(key -> ApiKeyVerifier.Verification.invalid("Invalid API key"),new ObjectMapper().findAndRegisterModules(),false);
        var request=new MockHttpServletRequest();request.addHeader("Authorization","Bearer sk_test_example");
        var response=new MockHttpServletResponse();var called=new AtomicBoolean();
        filter.doFilter(request,response,(req,res)->called.set(true));
        assertEquals(401,response.getStatus());assertFalse(called.get());
    }
}
