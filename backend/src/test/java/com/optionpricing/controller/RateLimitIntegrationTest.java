package com.optionpricing.controller;

import com.optionpricing.service.MarketDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "rate-limit.requests-per-minute=5",
        "rate-limit.pricing-per-minute=2",
        "spring.datasource.url=jdbc:h2:mem:optionpricing-ratelimit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RateLimitIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketDataService marketDataService;

    // Each test sends one request more than two full windows could allow, so a 429 is
    // guaranteed even if the clock crosses a minute boundary part-way through.

    @Test
    void clientIsLimitedOnceItExceedsTheGeneralBudget() throws Exception {
        List<MvcResult> results = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            results.add(mockMvc.perform(get("/api/v1/history").header("X-Forwarded-For", "203.0.113.1")).andReturn());
        }

        MvcResult limited = results.stream()
                .filter(result -> result.getResponse().getStatus() == 429)
                .findFirst()
                .orElseThrow();
        assertThat(limited.getResponse().getHeader("Retry-After")).isNotNull();
        assertThat(limited.getResponse().getContentAsString()).contains("Too many requests");
    }

    @Test
    void pricingHasItsOwnTighterBudget() throws Exception {
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            // An invalid body is enough: the limiter runs before validation, and nothing is saved.
            statuses.add(mockMvc.perform(post("/api/v1/price")
                            .header("X-Forwarded-For", "203.0.113.2")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andReturn().getResponse().getStatus());
        }

        assertThat(statuses).contains(400, 429);
    }

    @Test
    void limitsAreTrackedPerClient() throws Exception {
        for (int i = 0; i < 11; i++) {
            mockMvc.perform(get("/api/v1/history").header("X-Forwarded-For", "203.0.113.3"));
        }

        mockMvc.perform(get("/api/v1/history").header("X-Forwarded-For", "203.0.113.4"))
                .andExpect(status().isOk());
    }

    @Test
    void healthCheckIsNeverLimited() throws Exception {
        for (int i = 0; i < 11; i++) {
            mockMvc.perform(get("/health").header("X-Forwarded-For", "203.0.113.5"))
                    .andExpect(status().isOk());
        }
    }
}
