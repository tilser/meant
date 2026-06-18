package com.meant.api.common.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meant.api.module.cart.exception.CartException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalApiExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new TestErrorController())
                .setControllerAdvice(new GlobalApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void validationErrorsReturnSanitizedProblemDetail() throws Exception {
        mockMvc.perform(post("/test-errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Request validation failed."))
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.path").value("/test-errors/validation"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(content().string(not(containsString("MethodArgumentNotValidException"))))
                .andExpect(content().string(not(containsString("stackTrace"))));
    }

    @Test
    void upstreamErrorsDoNotLeakRawMessages() throws Exception {
        mockMvc.perform(get("/test-errors/upstream"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.detail").value("Upstream service is temporarily unavailable."))
                .andExpect(jsonPath("$.code").value("upstream_service_error"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(content().string(not(containsString("OpenRouter stream failed"))))
                .andExpect(content().string(not(containsString("internal-upstream.test"))))
                .andExpect(content().string(not(containsString("stackTrace"))));
    }

    @Test
    void notFoundBusinessErrorsKeepStatusWithoutLeakingIdentifiers() throws Exception {
        mockMvc.perform(get("/test-errors/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("The requested resource was not found."))
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(content().string(not(containsString("00000000-0000-0000-0000-000000000001"))));
    }

    @Test
    void businessExceptionMessageTextDoesNotDetermineStatus() throws Exception {
        mockMvc.perform(get("/test-errors/not-found-message-only"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("The request could not be processed."))
                .andExpect(jsonPath("$.code").value("bad_request"))
                .andExpect(content().string(not(containsString("not found"))));
    }

    @Test
    void constraintViolationsReturnValidationFailedCode() throws Exception {
        mockMvc.perform(get("/test-errors/constraint-violation"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Request validation failed."))
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void unhandledErrorsReturnGenericProblemDetail() throws Exception {
        mockMvc.perform(get("/test-errors/unhandled"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.code").value("internal_error"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(content().string(not(containsString("select * from users"))))
                .andExpect(content().string(not(containsString("com.meant.Secret"))))
                .andExpect(content().string(not(containsString("stackTrace"))));
    }

    @RestController
    @RequestMapping("/test-errors")
    private static class TestErrorController {

        @PostMapping("/validation")
        void validation(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/upstream")
        void upstream() {
            throw new OpenRouterException(
                    "OpenRouter stream failed: 500 from https://internal-upstream.test/private"
            );
        }

        @GetMapping("/not-found")
        void notFound() {
            throw CartException.notFound("Cart not found: 00000000-0000-0000-0000-000000000001");
        }

        @GetMapping("/not-found-message-only")
        void notFoundMessageOnly() {
            throw new CartException("Cart processor not found in remote payload");
        }

        @GetMapping("/constraint-violation")
        void constraintViolation() {
            throw new ConstraintViolationException("Service validation failed", Set.of());
        }

        @GetMapping("/unhandled")
        void unhandled() {
            throw new IllegalStateException("SQL detail: select * from users at com.meant.Secret");
        }
    }

    private record TestRequest(@NotBlank String name) {
    }
}
