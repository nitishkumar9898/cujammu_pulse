package com.college.events.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiValidationPrivacyTest {
    record Input(@Size(max=4) String value) { }
    @RestController static class SyntheticController {
        @PostMapping("/synthetic-validation") Input submit(@Valid @RequestBody Input input) { return input; }
    }
    @Test void parserAndBeanValidationNeverEchoRejectedValues() throws Exception {
        var mvc=standaloneSetup(new SyntheticController()).setControllerAdvice(new ApiFailureHandler()).build();
        for(String body : new String[]{"{\"value\":\"SYNTHETIC_PRIVATE_VALUE\"}", "{\"value\":SYNTHETIC_PRIVATE_VALUE}"}) {
            mvc.perform(post("/synthetic-validation").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(content().json("{\"error\":\"INVALID_REQUEST\"}",true))
                    .andExpect(header().string("Cache-Control","no-store"));
        }
    }
}
