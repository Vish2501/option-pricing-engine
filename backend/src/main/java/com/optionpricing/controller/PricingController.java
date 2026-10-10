package com.optionpricing.controller;

import com.optionpricing.dto.ComparisonResponse;
import com.optionpricing.dto.MarketDataResponse;
import com.optionpricing.dto.PricingAnalyticsResponse;
import com.optionpricing.dto.PricingHistoryResponse;
import com.optionpricing.dto.PricingRequest;
import com.optionpricing.dto.PricingResponse;
import com.optionpricing.dto.TickerSearchResult;
import com.optionpricing.service.OptionPricingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Option Pricing", description = "Pricing, market-data, history, and analytics endpoints.")
public class PricingController {
    public static final String TOTAL_COUNT_HEADER = "X-Total-Count";

    private final OptionPricingService service;

    public PricingController(OptionPricingService service) {
        this.service = service;
    }

    @PostMapping("/price")
    @Operation(summary = "Price an option with Black-Scholes, Monte Carlo, and Binomial Tree models, including an American-exercise tree price.")
    PricingResponse price(@Valid @RequestBody PricingRequest request) {
        return service.price(request);
    }

    @GetMapping("/stock/{ticker}")
    @Operation(summary = "Fetch live price and historical volatility for a ticker.")
    MarketDataResponse stock(@PathVariable String ticker) {
        return service.stock(ticker);
    }

    @GetMapping("/tickers/search")
    @Operation(summary = "Search for stocks and ETFs by company name or symbol.")
    List<TickerSearchResult> searchTickers(@RequestParam("q") String query) {
        return service.searchTickers(query);
    }

    @GetMapping("/price/compare/{ticker}")
    @Operation(summary = "Compare call and put pricing for an at-the-money option.")
    ComparisonResponse compare(@PathVariable String ticker) {
        return service.compare(ticker);
    }

    @GetMapping("/history")
    @Operation(summary = "Return saved pricing requests, newest first. Filter with ticker, page with page and size; the X-Total-Count header holds the total number of matching rows.")
    ResponseEntity<List<PricingHistoryResponse>> history(
            @RequestParam(required = false) String ticker,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Page<PricingHistoryResponse> result = service.history(ticker, page, size);
        return ResponseEntity.ok()
                .header(TOTAL_COUNT_HEADER, String.valueOf(result.getTotalElements()))
                .body(result.getContent());
    }

    @GetMapping("/history/analytics")
    @Operation(summary = "Return aggregate pricing request analytics.")
    PricingAnalyticsResponse analytics() {
        return service.analytics();
    }
}
