package com.optionpricing.controller;

import com.optionpricing.repository.PricingRequestLogRepository;
import com.optionpricing.service.MarketDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs against a real PostgreSQL database, the same engine as production, instead of H2.
 * The schema is built by the Flyway migrations and Hibernate only validates it, so this
 * fails if an entity and the migrations ever drift apart, and it proves the aggregate
 * queries run on Postgres. Skipped unless POSTGRES_TEST_URL is set; CI sets it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_URL", matches = ".+")
@TestPropertySource(properties = {
        "spring.datasource.url=${POSTGRES_TEST_URL}",
        "spring.datasource.username=${POSTGRES_TEST_USERNAME:postgres}",
        "spring.datasource.password=${POSTGRES_TEST_PASSWORD:postgres}",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class PostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PricingRequestLogRepository repository;

    @MockBean
    private MarketDataService marketDataService;

    @Test
    void pricesSavesAndAggregatesOnPostgres() throws Exception {
        repository.deleteAll();
        String expiry = LocalDate.now().plusDays(30).toString();

        for (String ticker : new String[]{"AAPL", "AAPL", "MSFT"}) {
            mockMvc.perform(post("/api/v1/price")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"ticker": "%s", "spotPrice": 100, "strike": 100, "expiry": "%s",
                                     "optionType": "PUT", "volatility": 0.2, "dividendYield": 0.01, "marketPrice": 3}
                                    """.formatted(ticker, expiry)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/history").param("ticker", "AAPL").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].dividendYield").value(0.01));

        mockMvc.perform(get("/api/v1/history/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(3))
                .andExpect(jsonPath("$.uniqueTickers").value(2))
                .andExpect(jsonPath("$.requestsByTicker.AAPL").value(2))
                .andExpect(jsonPath("$.averageModelSpread").isNumber());
    }
}
