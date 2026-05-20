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

import java.time.LocalDate;

import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:optionpricing;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PricingControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketDataService marketDataService;

    @Test
    void priceEndpointReturnsAllModelsGreeksAndImpliedVolatility() throws Exception {
        String expiry = LocalDate.now().plusDays(7).toString();

        mockMvc.perform(post("/api/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ticker": "AAPL",
                                  "spotPrice": 226.5,
                                  "strike": 207.5,
                                  "expiry": "%s",
                                  "optionType": "CALL",
                                  "volatility": 0.2191,
                                  "riskFreeRate": 0.05,
                                  "marketPrice": 22.25
                                }
                                """.formatted(expiry)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.blackScholesPrice").value(closeTo(19.17, 0.05)))
                .andExpect(jsonPath("$.monteCarloPrice").exists())
                .andExpect(jsonPath("$.binomialTreePrice").value(closeTo(19.17, 0.08)))
                .andExpect(jsonPath("$.impliedVolatility").value(closeTo(0.8216, 0.02)))
                .andExpect(jsonPath("$.greeks.delta").value(closeTo(0.9983, 0.01)));
    }

    @Test
    void versionedPriceEndpointRejectsInvalidTicker() throws Exception {
        String expiry = LocalDate.now().plusDays(7).toString();

        mockMvc.perform(post("/api/v1/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ticker": "AAPL<script>",
                                  "spotPrice": 226.5,
                                  "strike": 207.5,
                                  "expiry": "%s",
                                  "optionType": "CALL",
                                  "volatility": 0.2191,
                                  "riskFreeRate": 0.05
                                }
                                """.formatted(expiry)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void historyEndpointReturnsLoggedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/history"))
                .andExpect(status().isOk());
    }

    @Test
    void analyticsEndpointReturnsHistoricalRequestSummary() throws Exception {
        mockMvc.perform(get("/api/v1/history/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").exists())
                .andExpect(jsonPath("$.uniqueTickers").exists())
                .andExpect(jsonPath("$.averageModelSpread").exists());
    }
}
