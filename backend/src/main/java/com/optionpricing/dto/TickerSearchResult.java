package com.optionpricing.dto;

public record TickerSearchResult(
        String symbol,
        String name,
        String exchange
) {
}
