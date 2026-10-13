package com.payflow.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RequestParameterErrorTest {
    @RestController
    static class ParameterController {
        @GetMapping("/parameter/{id}") String detail(@PathVariable("id") UUID id) { return id.toString(); }
        @GetMapping("/parameter") String list(@RequestParam("limit") int limit) { return Integer.toString(limit); }
        @GetMapping("/parameter/outage") String outage() { throw new IllegalStateException("private service detail"); }
    }

    @Test void malformedUuidIs400WithoutEchoingTheInput() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ParameterController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/parameter/invalid-input"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request parameter is invalid"));
    }

    @Test void malformedNumericQueryIs400WithoutEchoingTheInput() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ParameterController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/parameter").param("limit", "invalid-input"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request parameter is invalid"));
    }

    @Test void validParametersStillReachTheController() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ParameterController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        UUID id = UUID.randomUUID();
        mvc.perform(get("/parameter/" + id)).andExpect(status().isOk()).andExpect(content().string(id.toString()));
        mvc.perform(get("/parameter").param("limit", "10"))
                .andExpect(status().isOk()).andExpect(content().string("10"));
    }

    @Test void genuineServiceErrorsRemain500WithoutInternalDetails() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ParameterController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/parameter/outage")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
