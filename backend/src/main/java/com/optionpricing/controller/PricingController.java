package com.optionpricing.controller;

import com.optionpricing.dto.ComparisonResponse;
import com.optionpricing.dto.MarketDataResponse;
import com.optionpricing.dto.PricingAnalyticsResponse;
import com.optionpricing.dto.PricingHistoryResponse;
import com.optionpricing.dto.PricingRequest;
import com.optionpricing.dto.PricingResponse;
import com.optionpricing.service.OptionPricingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/v1", "/api"})
@Tag(name = "Option Pricing", description = "Pricing, market-data, history, and analytics endpoints.")
public class PricingController {
    private final OptionPricingService service;

    public PricingController(OptionPricingService service) {
        this.service = service;
    }

    @PostMapping("/price")
    @Operation(summary = "Price a European option with Black-Scholes, Monte Carlo, and Binomial Tree models.")
    PricingResponse price(@Valid @RequestBody PricingRequest request) {
        return service.price(request);
    }

    @GetMapping("/stock/{ticker}")
    @Operation(summary = "Fetch live price and historical volatility for a ticker.")
    MarketDataResponse stock(@PathVariable String ticker) {
        return service.stock(ticker);
    }

    @GetMapping("/price/compare/{ticker}")
    @Operation(summary = "Compare call and put pricing for an at-the-money option.")
    ComparisonResponse compare(@PathVariable String ticker) {
        return service.compare(ticker);
    }

    @GetMapping("/history")
    @Operation(summary = "Return recent pricing requests. Protected by X-API-Key when API_KEY is configured.")
    List<PricingHistoryResponse> history() {
        return service.history();
    }

    @GetMapping("/history/analytics")
    @Operation(summary = "Return aggregate pricing request analytics. Protected by X-API-Key when API_KEY is configured.")
    PricingAnalyticsResponse analytics() {
        return service.analytics();
    }
}
