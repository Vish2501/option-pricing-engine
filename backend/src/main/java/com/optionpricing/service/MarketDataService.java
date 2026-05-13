package com.optionpricing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.optionpricing.dto.MarketDataResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

@Service
public class MarketDataService {
    private static final String YAHOO_CHART_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/{ticker}?range=3mo&interval=1d";

    private final RestClient restClient;

    public MarketDataService(RestClient restClient) {
        this.restClient = restClient;
    }

    public MarketDataResponse getMarketData(String ticker) {
        String normalizedTicker = ticker.trim().toUpperCase();
        JsonNode result = fetchChart(normalizedTicker);
        double price = result.path("meta").path("regularMarketPrice").asDouble(Double.NaN);
        List<Double> closes = closingPrices(result);

        if (!Double.isFinite(price) || price <= 0) {
            if (closes.isEmpty()) {
                throw new IllegalArgumentException("No live or closing price available for ticker " + normalizedTicker);
            }
            price = closes.get(closes.size() - 1);
        }

        return new MarketDataResponse(normalizedTicker, price, historicalVolatility(closes));
    }

    private JsonNode fetchChart(String ticker) {
        try {
            JsonNode response = restClient.get()
                    .uri(YAHOO_CHART_URL, ticker)
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode result = response == null ? null : response.path("chart").path("result").path(0);
            if (result == null || result.isMissingNode() || result.isNull()) {
                throw new IllegalArgumentException("Invalid ticker or no Yahoo Finance data available: " + ticker);
            }
            return result;
        } catch (RestClientException ex) {
            throw new IllegalArgumentException("Unable to fetch Yahoo Finance data for ticker " + ticker, ex);
        }
    }

    private List<Double> closingPrices(JsonNode result) {
        JsonNode closeNode = result.path("indicators").path("quote").path(0).path("close");
        List<Double> closes = new ArrayList<>();
        if (!closeNode.isArray()) {
            return closes;
        }

        closeNode.forEach(close -> {
            if (close.isNumber() && close.asDouble() > 0) {
                closes.add(close.asDouble());
            }
        });
        return closes;
    }

    private double historicalVolatility(List<Double> closes) {
        if (closes.size() < 2) {
            throw new IllegalArgumentException("At least two closing prices are required for historical volatility.");
        }

        int start = Math.max(1, closes.size() - 30);
        List<Double> returns = new ArrayList<>();
        for (int i = start; i < closes.size(); i++) {
            returns.add(Math.log(closes.get(i) / closes.get(i - 1)));
        }

        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = returns.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2))
                .sum() / Math.max(returns.size() - 1, 1);
        return Math.sqrt(variance) * Math.sqrt(252);
    }
}
