package com.foodloop.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FakeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validationFailure_returns400WithFieldErrors() throws Exception {
        mvc.perform(post("/t/valid").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.name").value("Name is required"));
    }

    @Test
    void malformedJson_returns400() throws Exception {
        mvc.perform(post("/t/valid").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void notFound_returns404() throws Exception {
        mvc.perform(get("/t/missing")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Thing not found"));
    }

    @Test
    void conflict_returns409() throws Exception {
        mvc.perform(get("/t/conflict")).andExpect(status().isConflict());
    }

    @Test
    void forbidden_returns403() throws Exception {
        mvc.perform(get("/t/forbidden")).andExpect(status().isForbidden());
    }

    @Test
    void unexpectedError_returns500WithoutLeakingDetails() throws Exception {
        mvc.perform(get("/t/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("db-password-123"))));
    }

    @RestController
    @RequestMapping("/t")
    static class FakeController {
        record Body(@NotBlank(message = "Name is required") String name) {}

        @PostMapping("/valid")
        String valid(@Valid @RequestBody Body body) { return "ok"; }

        @GetMapping("/missing")
        String missing() { throw new ResourceNotFoundException("Thing not found"); }

        @GetMapping("/conflict")
        String conflict() { throw new ConflictException("conflict"); }

        @GetMapping("/forbidden")
        String forbidden() { throw new ForbiddenException("nope"); }

        @GetMapping("/boom")
        String boom() { throw new IllegalStateException("db-password-123"); }
    }
}
