package com.optionpricing.dto;

public record ComparisonResponse(
        String ticker,
        double spotPrice,
        double historicalVolatility,
        PricingResponse call,
        PricingResponse put
) {
}
