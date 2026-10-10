package com.optionpricing.service;

import com.optionpricing.dto.*;
import com.optionpricing.entity.PricingRequestLog;
import com.optionpricing.model.ExerciseStyle;
import com.optionpricing.model.OptionType;
import com.optionpricing.repository.PricingRequestLogRepository;
import com.optionpricing.repository.PricingRequestLogRepository.AnalyticsSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OptionPricingService {
    private final double defaultRiskFreeRate;
    private final double defaultDividendYield;
    private final BlackScholesService blackScholesService;
    private final MonteCarloService monteCarloService;
    private final BinomialTreeService binomialTreeService;
    private final MarketDataService marketDataService;
    private final ImpliedVolatilityService impliedVolatilityService;
    private final PricingRequestLogRepository repository;

    public OptionPricingService(
            @Value("${pricing.defaults.risk-free-rate:0.05}") double defaultRiskFreeRate,
            @Value("${pricing.defaults.dividend-yield:0.0}") double defaultDividendYield,
            BlackScholesService blackScholesService,
            MonteCarloService monteCarloService,
            BinomialTreeService binomialTreeService,
            MarketDataService marketDataService,
            ImpliedVolatilityService impliedVolatilityService,
            PricingRequestLogRepository repository
    ) {
        this.defaultRiskFreeRate = defaultRiskFreeRate;
        this.defaultDividendYield = defaultDividendYield;
        this.blackScholesService = blackScholesService;
        this.monteCarloService = monteCarloService;
        this.binomialTreeService = binomialTreeService;
        this.marketDataService = marketDataService;
        this.impliedVolatilityService = impliedVolatilityService;
        this.repository = repository;
    }

    public PricingResponse price(PricingRequest request) {
        PricingResponse response = calculate(request, true);
        repository.save(toEntity(response));
        return response;
    }

    public MarketDataResponse stock(String ticker) {
        return marketDataService.getMarketData(ticker);
    }

    public List<TickerSearchResult> searchTickers(String query) {
        return marketDataService.search(query);
    }

    public ComparisonResponse compare(String ticker) {
        MarketDataResponse market = stock(ticker);
        LocalDate expiry = LocalDate.now().plusDays(30);

        PricingResponse call = calculate(new PricingRequest(
                market.ticker(), market.livePrice(), market.livePrice(), expiry, OptionType.CALL,
                market.historicalVolatility(), defaultRiskFreeRate, null, null
        ), false);

        PricingResponse put = calculate(new PricingRequest(
                market.ticker(), market.livePrice(), market.livePrice(), expiry, OptionType.PUT,
                market.historicalVolatility(), defaultRiskFreeRate, null, null
        ), false);

        return new ComparisonResponse(market.ticker(), market.livePrice(), market.historicalVolatility(), call, put);
    }

    public List<PricingHistoryResponse> history() {
        return repository.findTop50ByOrderByRequestedAtDesc().stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    public PricingAnalyticsResponse analytics() {
        AnalyticsSummary summary = repository.summarize();
        // LinkedHashMap keeps the query's busiest-ticker-first order in the JSON response.
        Map<String, Long> requestsByTicker = new LinkedHashMap<>();
        repository.countRequestsByTicker().forEach(row -> requestsByTicker.put(row.getTicker(), row.getRequests()));

        return new PricingAnalyticsResponse(
                summary.getTotalRequests(),
                summary.getUniqueTickers(),
                requestsByTicker,
                orZero(summary.getAverageBlackScholesPrice()),
                orZero(summary.getAverageMonteCarloPrice()),
                orZero(summary.getAverageBinomialTreePrice()),
                orZero(summary.getAverageModelSpread())
        );
    }

    private PricingResponse calculate(PricingRequest request, boolean allowMarketLookup) {
        if (!request.expiry().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Expiry must be in the future.");
        }

        String ticker = request.ticker().trim().toUpperCase();
        MarketDataResponse market = null;
        if (allowMarketLookup && (request.spotPrice() == null || request.volatility() == null)) {
            market = stock(ticker);
        } else if (allowMarketLookup) {
            // Inputs were typed in by hand, so nothing has checked the symbol is real yet.
            marketDataService.requireKnownTicker(ticker);
        }

        double spot = request.spotPrice() != null ? request.spotPrice() : market.livePrice();
        double volatility = request.volatility() != null ? request.volatility() : market.historicalVolatility();
        double rate = request.riskFreeRate() != null ? request.riskFreeRate() : defaultRiskFreeRate;
        double dividendYield = request.dividendYield() != null ? request.dividendYield() : defaultDividendYield;
        double timeYears = ChronoUnit.DAYS.between(LocalDate.now(), request.expiry()) / 365.0;

        PricingResult blackScholesResult = blackScholesService.priceWithGreeks(request.optionType(), spot, request.strike(), timeYears, rate, dividendYield, volatility);
        double blackScholes = blackScholesResult.price();
        MonteCarloResult monteCarlo = monteCarloService.simulate(request.optionType(), spot, request.strike(), timeYears, rate, dividendYield, volatility);
        double binomial = binomialTreeService.price(request.optionType(), spot, request.strike(), timeYears, rate, dividendYield, volatility);
        double americanBinomial = binomialTreeService.price(request.optionType(), ExerciseStyle.AMERICAN, spot, request.strike(), timeYears, rate, dividendYield, volatility);
        GreeksResponse greeks = blackScholesResult.greeks();
        Double impliedVolatility = request.marketPrice() == null ? null : impliedVolatilityService.impliedVolatility(
                request.optionType(), request.marketPrice(), spot, request.strike(), timeYears, rate, dividendYield);

        return new PricingResponse(
                ticker, spot, request.strike(), request.expiry(), timeYears, request.optionType(), rate, volatility,
                blackScholes, monteCarlo.price(), monteCarlo.standardError(), binomial, americanBinomial,
                americanBinomial - binomial, impliedVolatility, greeks);
    }

    private PricingRequestLog toEntity(PricingResponse response) {
        PricingRequestLog log = new PricingRequestLog();
        log.setTicker(response.ticker());
        log.setSpotPrice(response.spotPrice());
        log.setStrike(response.strike());
        log.setExpiry(response.expiry());
        log.setOptionType(response.optionType());
        log.setRiskFreeRate(response.riskFreeRate());
        log.setVolatility(response.volatility());
        log.setBlackScholesPrice(response.blackScholesPrice());
        log.setMonteCarloPrice(response.monteCarloPrice());
        log.setBinomialTreePrice(response.binomialTreePrice());
        log.setImpliedVolatility(response.impliedVolatility());
        log.setDelta(response.greeks().delta());
        log.setGamma(response.greeks().gamma());
        log.setVega(response.greeks().vega());
        log.setTheta(response.greeks().theta());
        log.setRho(response.greeks().rho());
        return log;
    }

    private PricingHistoryResponse toHistoryResponse(PricingRequestLog log) {
        return new PricingHistoryResponse(
                log.getId(),
                log.getTicker(),
                log.getSpotPrice(),
                log.getStrike(),
                log.getExpiry(),
                log.getOptionType(),
                log.getRiskFreeRate(),
                log.getVolatility(),
                log.getBlackScholesPrice(),
                log.getMonteCarloPrice(),
                log.getBinomialTreePrice(),
                log.getImpliedVolatility(),
                log.getDelta(),
                log.getGamma(),
                log.getVega(),
                log.getTheta(),
                log.getRho(),
                log.getRequestedAt()
        );
    }

    private double orZero(Double value) {
        return value == null ? 0.0 : value;
    }
}
