package com.optionpricing.dto;

import com.optionpricing.model.OptionType;

import java.time.LocalDate;

public record PricingResponse(
        String ticker,
        double spotPrice,
        double strike,
        LocalDate expiry,
        double timeToMaturityYears,
        OptionType optionType,
        double riskFreeRate,
        double volatility,
        double blackScholesPrice,
        double monteCarloPrice,
        // Uncertainty of the Monte Carlo estimate; price +/- 1.96 of these is a 95% confidence interval.
        double monteCarloStandardError,
        double binomialTreePrice,
        // Same tree, but allowing exercise at any time before expiry.
        double americanBinomialPrice,
        // What the right to exercise early is worth: American price minus European price.
        double earlyExercisePremium,
        Double impliedVolatility,
        GreeksResponse greeks
) {
}
