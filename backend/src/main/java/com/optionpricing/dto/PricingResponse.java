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
        double binomialTreePrice,
        Double impliedVolatility,
        GreeksResponse greeks
) {
}
