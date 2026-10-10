package com.optionpricing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.optionpricing.dto.MarketDataResponse;
import com.optionpricing.dto.TickerSearchResult;
import com.optionpricing.model.TickerSymbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MarketDataService {
    private static final String YAHOO_CHART_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/{ticker}?range=3mo&interval=1d";

    private static final String YAHOO_SEARCH_URL =
            "https://query1.finance.yahoo.com/v1/finance/search?q={query}&quotesCount=10&newsCount=0";

    // Daily closes and a delayed quote do not change second to second, so a short cache keeps
    // us well clear of Yahoo's rate limits without making the numbers meaningfully stale.
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);
    private static final int MAX_SEARCH_QUERY_LENGTH = 40;
    private static final int MAX_SEARCH_RESULTS = 6;
    private static final Set<String> SEARCHABLE_TYPES = Set.of("EQUITY", "ETF");

    private static final Logger logger = LoggerFactory.getLogger(MarketDataService.class);

    private final RestClient restClient;
    private final Map<String, CachedMarketData> cache = new ConcurrentHashMap<>();

    public MarketDataService(RestClient restClient) {
        this.restClient = restClient;
    }

    public MarketDataResponse getMarketData(String ticker) {
        String normalizedTicker = TickerSymbol.normalize(ticker);
        CachedMarketData cached = cache.get(normalizedTicker);
        if (cached != null && cached.fetchedAt().plus(CACHE_TTL).isAfter(Instant.now())) {
            return cached.data();
        }

        MarketDataResponse data = fetchMarketData(normalizedTicker);
        logger.info("Fetched market data for {} from Yahoo Finance", normalizedTicker);
        cache.put(normalizedTicker, new CachedMarketData(data, Instant.now()));
        return data;
    }

    // Rejects symbols Yahoo does not recognise. If Yahoo itself is down we let the request
    // through: pricing with hand-entered inputs should not depend on a third party being up.
    public void requireKnownTicker(String ticker) {
        try {
            getMarketData(ticker);
        } catch (MarketDataUnavailableException ex) {
            logger.warn("Could not verify ticker {} because market data is unavailable; allowing the request", ticker);
        }
    }

    public List<TickerSearchResult> search(String query) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        if (trimmed.length() > MAX_SEARCH_QUERY_LENGTH) {
            throw new IllegalArgumentException("Search text must be at most " + MAX_SEARCH_QUERY_LENGTH + " characters.");
        }

        JsonNode quotes;
        try {
            JsonNode response = restClient.get()
                    .uri(YAHOO_SEARCH_URL, trimmed)
                    .retrieve()
                    .body(JsonNode.class);
            quotes = response == null ? null : response.path("quotes");
        } catch (RestClientException ex) {
            logger.warn("Yahoo Finance search request failed: {}", ex.getMessage());
            throw new MarketDataUnavailableException("Unable to search Yahoo Finance for " + trimmed, ex);
        }

        List<TickerSearchResult> results = new ArrayList<>();
        if (quotes == null || !quotes.isArray()) {
            return results;
        }
        for (JsonNode quote : quotes) {
            String symbol = quote.path("symbol").asText("");
            if (results.size() < MAX_SEARCH_RESULTS
                    && SEARCHABLE_TYPES.contains(quote.path("quoteType").asText(""))
                    // Only symbols the pricing form accepts; this leaves out futures (=F) and indices (^).
                    && TickerSymbol.isValid(symbol)) {
                String name = quote.path("longname").asText(quote.path("shortname").asText(symbol));
                results.add(new TickerSearchResult(symbol, name, quote.path("exchDisp").asText("")));
            }
        }
        return results;
    }

    private MarketDataResponse fetchMarketData(String normalizedTicker) {
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
        } catch (HttpClientErrorException.NotFound ex) {
            // Yahoo answers 404 for symbols it does not know - that is the caller's mistake, not an outage.
            throw new IllegalArgumentException("Invalid ticker or no Yahoo Finance data available: " + ticker, ex);
        } catch (RestClientException ex) {
            logger.warn("Yahoo Finance chart request failed for {}: {}", ticker, ex.getMessage());
            throw new MarketDataUnavailableException("Unable to fetch Yahoo Finance data for ticker " + ticker, ex);
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

    private record CachedMarketData(MarketDataResponse data, Instant fetchedAt) {
    }
}
