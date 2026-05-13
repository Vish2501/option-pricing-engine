package com.optionpricing.dto;

import java.util.Map;

public record PricingAnalyticsResponse(
        long totalRequests,
        long uniqueTickers,
        Map<String, Long> requestsByTicker,
        double averageBlackScholesPrice,
        double averageMonteCarloPrice,
        double averageBinomialTreePrice,
        double averageModelSpread
) {
}
