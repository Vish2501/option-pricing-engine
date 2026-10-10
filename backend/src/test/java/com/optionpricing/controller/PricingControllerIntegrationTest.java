package com.optionpricing.controller;

import com.optionpricing.entity.PricingRequestLog;
import com.optionpricing.model.OptionType;
import com.optionpricing.repository.PricingRequestLogRepository;
import com.optionpricing.service.MarketDataService;
import com.optionpricing.service.MarketDataUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    @Autowired
    private PricingRequestLogRepository repository;

    @Test
    void priceEndpointReturnsAllModelsGreeksAndImpliedVolatility() throws Exception {
        String expiry = LocalDate.now().plusDays(7).toString();

        mockMvc.perform(post("/api/v1/price")
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
                .andExpect(jsonPath("$.monteCarloStandardError").isNumber())
                .andExpect(jsonPath("$.americanBinomialPrice").value(closeTo(19.17, 0.08)))
                // A call on a stock with no dividends is never worth exercising early.
                .andExpect(jsonPath("$.earlyExercisePremium").value(closeTo(0.0, 1e-9)))
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
    void priceEndpointRejectsMissingStrikeWithValidationMessage() throws Exception {
        String expiry = LocalDate.now().plusDays(7).toString();

        mockMvc.perform(post("/api/v1/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ticker": "AAPL",
                                  "spotPrice": 226.5,
                                  "expiry": "%s",
                                  "optionType": "CALL",
                                  "volatility": 0.2191
                                }
                                """.formatted(expiry)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("strike must not be null"));
    }

    @Test
    void priceEndpointReturnsAndSavesDividendYield() throws Exception {
        repository.deleteAll();
        String expiry = LocalDate.now().plusDays(30).toString();

        mockMvc.perform(post("/api/v1/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker": "KO", "spotPrice": 60, "strike": 60, "expiry": "%s",
                                 "optionType": "PUT", "volatility": 0.18, "dividendYield": 0.03}
                                """.formatted(expiry)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dividendYield").value(0.03));

        mockMvc.perform(get("/api/v1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("KO"))
                .andExpect(jsonPath("$[0].dividendYield").value(0.03));
    }

    @Test
    void impliedVolatilityIsNullWhenTheMarketPriceIsImpossible() throws Exception {
        String expiry = LocalDate.now().plusDays(30).toString();

        // A call cannot cost more than the stock itself.
        mockMvc.perform(post("/api/v1/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker": "AAPL", "spotPrice": 100, "strike": 100, "expiry": "%s",
                                 "optionType": "CALL", "volatility": 0.2, "marketPrice": 150}
                                """.formatted(expiry)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.impliedVolatility").value(nullValue()));
    }

    @Test
    void historyIsNewestFirstAndCanBeFilteredAndPaged() throws Exception {
        repository.deleteAll();
        repository.save(log("AAPL", 10.0, 10.4, 10.1));
        repository.save(log("MSFT", 30.0, 30.0, 30.2));
        repository.save(log("AAPL", 20.0, 19.0, 20.5));

        mockMvc.perform(get("/api/v1/history"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].blackScholesPrice").value(20.0))
                .andExpect(jsonPath("$[2].blackScholesPrice").value(10.0));

        mockMvc.perform(get("/api/v1/history").param("ticker", "aapl"))
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[1].ticker").value("AAPL"));

        mockMvc.perform(get("/api/v1/history").param("page", "1").param("size", "2"))
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].blackScholesPrice").value(10.0));
    }

    @Test
    void historyRejectsBadPagingAndFilterValues() throws Exception {
        mockMvc.perform(get("/api/v1/history").param("size", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
        mockMvc.perform(get("/api/v1/history").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("page has an invalid value."));
        mockMvc.perform(get("/api/v1/history").param("ticker", "AAPL<script>"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void malformedJsonAndUnknownOptionTypeUseTheStandardErrorShape() throws Exception {
        mockMvc.perform(post("/api/v1/price").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(post("/api/v1/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker": "AAPL", "strike": 100, "expiry": "2099-01-01", "optionType": "STRADDLE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void unknownPathAndWrongMethodUseTheStandardErrorShape() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
        mockMvc.perform(get("/api/v1/price"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").exists());
        mockMvc.perform(get("/api/v1/tickers/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void analyticsEndpointReturnsZeroesWhenNothingHasBeenPriced() throws Exception {
        repository.deleteAll();

        mockMvc.perform(get("/api/v1/history/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(0))
                .andExpect(jsonPath("$.uniqueTickers").value(0))
                .andExpect(jsonPath("$.averageModelSpread").value(0.0));
    }

    @Test
    void analyticsEndpointAggregatesLoggedRequests() throws Exception {
        repository.deleteAll();
        repository.save(log("AAPL", 10.0, 10.4, 10.1));
        repository.save(log("AAPL", 20.0, 19.0, 20.5));
        repository.save(log("MSFT", 30.0, 30.0, 30.2));

        mockMvc.perform(get("/api/v1/history/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(3))
                .andExpect(jsonPath("$.uniqueTickers").value(2))
                .andExpect(jsonPath("$.requestsByTicker.AAPL").value(2))
                .andExpect(jsonPath("$.requestsByTicker.MSFT").value(1))
                .andExpect(jsonPath("$.averageBlackScholesPrice").value(closeTo(20.0, 1e-9)))
                .andExpect(jsonPath("$.averageMonteCarloPrice").value(closeTo(19.8, 1e-9)))
                .andExpect(jsonPath("$.averageBinomialTreePrice").value(closeTo(20.2667, 1e-4)))
                // Spreads are 0.4, 1.5 and 0.2.
                .andExpect(jsonPath("$.averageModelSpread").value(closeTo(0.7, 1e-9)));
    }

    private PricingRequestLog log(String ticker, double blackScholes, double monteCarlo, double binomial) {
        PricingRequestLog log = new PricingRequestLog();
        log.setTicker(ticker);
        log.setSpotPrice(100);
        log.setStrike(100);
        log.setExpiry(LocalDate.now().plusDays(30));
        log.setOptionType(OptionType.CALL);
        log.setRiskFreeRate(0.05);
        log.setVolatility(0.2);
        log.setBlackScholesPrice(blackScholes);
        log.setMonteCarloPrice(monteCarlo);
        log.setBinomialTreePrice(binomial);
        return log;
    }

    @Test
    void healthEndpointIsAvailableForRenderChecks() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void stockEndpointReturnsBadGatewayWhenMarketDataIsUnavailable() throws Exception {
        when(marketDataService.getMarketData("AAPL"))
                .thenThrow(new MarketDataUnavailableException("Yahoo Finance unavailable", null));

        mockMvc.perform(get("/api/v1/stock/AAPL"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Yahoo Finance unavailable"));
    }
}
