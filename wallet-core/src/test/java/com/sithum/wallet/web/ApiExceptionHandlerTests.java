package com.sithum.wallet.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(ApiExceptionHandlerTests.ProbeController.class)
@Import({ApiExceptionHandler.class, CorrelationIdFilter.class,
        ApiExceptionHandlerTests.ProbeController.class})
@ExtendWith(OutputCaptureExtension.class)
class ApiExceptionHandlerTests {

    record Payload(@NotBlank String name, @Min(1) long amount) {
    }

    @RestController
    @Validated
    static class ProbeController {
        @PostMapping("/probe")
        void create(@RequestBody @jakarta.validation.Valid Payload payload) {
        }

        @GetMapping("/probe/boom")
        void boom() {
            throw new IllegalStateException("secret internal detail: jdbc:postgresql://db/prod");
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void validationFailureIs400WithPerFieldErrors() throws Exception {
        mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/probe"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field=='name')]").isNotEmpty())
                .andExpect(jsonPath("$.errors[?(@.field=='amount')]").isNotEmpty());
    }

    @Test
    void malformedBodyIs400ProblemJson() throws Exception {
        mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void unknownRouteIs404ProblemJson() throws Exception {
        mvc.perform(get("/no/such/route"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.instance").value("/no/such/route"));
    }

    @Test
    void unhandledExceptionIs500WithoutLeakingDetail() throws Exception {
        mvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Internal server error"))
                .andExpect(content().string(not(containsString("secret internal detail"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("at com.sithum"))));
    }

    @Test
    void everyResponseCarriesAGeneratedCorrelationIdMatchingTheBody() throws Exception {
        MvcResult result = mvc.perform(get("/no/such/route"))
                .andExpect(header().exists(CorrelationIdFilter.HEADER))
                .andReturn();
        String headerId = result.getResponse().getHeader(CorrelationIdFilter.HEADER);
        assertThat(result.getResponse().getContentAsString())
                .contains("\"correlationId\":\"" + headerId + "\"");
    }

    @Test
    void safeCallerSuppliedCorrelationIdIsEchoed() throws Exception {
        mvc.perform(get("/no/such/route").header(CorrelationIdFilter.HEADER, "req-123"))
                .andExpect(header().string(CorrelationIdFilter.HEADER, "req-123"))
                .andExpect(jsonPath("$.correlationId").value("req-123"));
    }

    @Test
    void unsafeCallerSuppliedCorrelationIdIsReplaced() throws Exception {
        MvcResult result = mvc.perform(get("/no/such/route")
                        .header(CorrelationIdFilter.HEADER, "bad id with spaces"))
                .andReturn();
        assertThat(result.getResponse().getHeader(CorrelationIdFilter.HEADER))
                .isNotEqualTo("bad id with spaces")
                .matches("[0-9a-f-]{36}");
    }

    @Test
    void correlationIdIsWrittenToTheLogs(CapturedOutput output) throws Exception {
        mvc.perform(get("/probe/boom").header(CorrelationIdFilter.HEADER, "log-check-1"));
        assertThat(output.getOut()).contains("[log-check-1]").contains("Unhandled exception");
    }
}
