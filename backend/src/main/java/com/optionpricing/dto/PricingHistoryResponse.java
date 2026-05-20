package com.optionpricing.dto;

import com.optionpricing.model.OptionType;

import java.time.Instant;
import java.time.LocalDate;

public record PricingHistoryResponse(
        Long id,
        String ticker,
        double spotPrice,
        double strike,
        LocalDate expiry,
        OptionType optionType,
        double riskFreeRate,
        double volatility,
        double blackScholesPrice,
        double monteCarloPrice,
        double binomialTreePrice,
        Double impliedVolatility,
        double delta,
        double gamma,
        double vega,
        double theta,
        double rho,
        Instant requestedAt
) {
}
