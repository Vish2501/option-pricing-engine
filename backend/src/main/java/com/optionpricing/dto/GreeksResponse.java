package com.optionpricing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Black-Scholes Greeks in raw (unscaled) units. Trading screens usually quote vega and rho
 * per 1 percentage point and theta per day - divide vega and rho by 100 and theta by 365
 * to get those.
 */
public record GreeksResponse(
        @Schema(description = "Change in option price per $1 move in the underlying.")
        double delta,
        @Schema(description = "Change in delta per $1 move in the underlying.")
        double gamma,
        @Schema(description = "Change in option price per 1.00 (100 percentage points) change in volatility. Divide by 100 for per-point vega.")
        double vega,
        @Schema(description = "Change in option price per year of time passing. Divide by 365 for per-day theta.")
        double theta,
        @Schema(description = "Change in option price per 1.00 (100 percentage points) change in the risk-free rate. Divide by 100 for per-point rho.")
        double rho
) {
}
