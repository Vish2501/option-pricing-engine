package com.optionpricing.controller;

import com.optionpricing.dto.ComparisonResponse;
import com.optionpricing.dto.MarketDataResponse;
import com.optionpricing.dto.PricingAnalyticsResponse;
import com.optionpricing.dto.PricingRequest;
import com.optionpricing.dto.PricingResponse;
import com.optionpricing.entity.PricingRequestLog;
import com.optionpricing.service.OptionPricingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PricingController {
    private final OptionPricingService service;

    public PricingController(OptionPricingService service) {
        this.service = service;
    }

    @PostMapping("/price")
    PricingResponse price(@Valid @RequestBody PricingRequest request) {
        return service.price(request);
    }

    @GetMapping("/stock/{ticker}")
    MarketDataResponse stock(@PathVariable String ticker) {
        return service.stock(ticker);
    }

    @GetMapping("/price/compare/{ticker}")
    ComparisonResponse compare(@PathVariable String ticker) {
        return service.compare(ticker);
    }

    @GetMapping("/history")
    List<PricingRequestLog> history() {
        return service.history();
    }

    @GetMapping("/history/analytics")
    PricingAnalyticsResponse analytics() {
        return service.analytics();
    }
}
