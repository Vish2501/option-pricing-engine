package com.optionpricing.controller;

import com.optionpricing.dto.MarketDataResponse;
import com.optionpricing.service.MarketDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "security.api-key=test-key",
        "spring.datasource.url=jdbc:h2:mem:optionpricing-secure;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PricingControllerSecurityIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketDataService marketDataService;

    @Test
    void historyRequiresApiKeyWhenConfigured() throws Exception {
        mockMvc.perform(get("/api/v1/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void historyAllowsValidApiKeyWhenConfigured() throws Exception {
        mockMvc.perform(get("/api/v1/history").header("X-API-Key", "test-key"))
                .andExpect(status().isOk());
    }

    @Test
    void publicMarketDataEndpointDoesNotRequireApiKey() throws Exception {
        when(marketDataService.getMarketData("AAPL"))
                .thenReturn(new MarketDataResponse("AAPL", 226.5, 0.2191));

        mockMvc.perform(get("/api/v1/stock/AAPL"))
                .andExpect(status().isOk());
    }
}
