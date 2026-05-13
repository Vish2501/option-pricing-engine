package com.optionpricing.dto;

public record MarketDataResponse(
        String ticker,
        double livePrice,
        double historicalVolatility
) {
}
