package com.optionpricing.service;

import com.optionpricing.dto.*;
import com.optionpricing.entity.PricingRequestLog;
import com.optionpricing.model.OptionType;
import com.optionpricing.repository.PricingRequestLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OptionPricingService {
    private final double defaultRiskFreeRate;
    private final BlackScholesService blackScholesService;
    private final MonteCarloService monteCarloService;
    private final BinomialTreeService binomialTreeService;
    private final MarketDataService marketDataService;
    private final ImpliedVolatilityService impliedVolatilityService;
    private final PricingRequestLogRepository repository;

    public OptionPricingService(
            @Value("${pricing.defaults.risk-free-rate:0.05}") double defaultRiskFreeRate,
            BlackScholesService blackScholesService,
            MonteCarloService monteCarloService,
            BinomialTreeService binomialTreeService,
            MarketDataService marketDataService,
            ImpliedVolatilityService impliedVolatilityService,
            PricingRequestLogRepository repository
    ) {
        this.defaultRiskFreeRate = defaultRiskFreeRate;
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

    public ComparisonResponse compare(String ticker) {
        MarketDataResponse market = stock(ticker);
        LocalDate expiry = LocalDate.now().plusDays(30);

        PricingResponse call = calculate(new PricingRequest(
                market.ticker(), market.livePrice(), market.livePrice(), expiry, OptionType.CALL,
                market.historicalVolatility(), defaultRiskFreeRate, null
        ), false);

        PricingResponse put = calculate(new PricingRequest(
                market.ticker(), market.livePrice(), market.livePrice(), expiry, OptionType.PUT,
                market.historicalVolatility(), defaultRiskFreeRate, null
        ), false);

        return new ComparisonResponse(market.ticker(), market.livePrice(), market.historicalVolatility(), call, put);
    }

    public List<PricingHistoryResponse> history() {
        return repository.findTop50ByOrderByRequestedAtDesc().stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    public PricingAnalyticsResponse analytics() {
        List<PricingRequestLog> logs = repository.findAll();
        Map<String, Long> requestsByTicker = logs.stream()
                .collect(Collectors.groupingBy(PricingRequestLog::getTicker, Collectors.counting()));

        return new PricingAnalyticsResponse(
                logs.size(),
                requestsByTicker.size(),
                requestsByTicker,
                average(logs.stream().mapToDouble(PricingRequestLog::getBlackScholesPrice).toArray()),
                average(logs.stream().mapToDouble(PricingRequestLog::getMonteCarloPrice).toArray()),
                average(logs.stream().mapToDouble(PricingRequestLog::getBinomialTreePrice).toArray()),
                average(logs.stream().mapToDouble(this::modelSpread).toArray())
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
        }

        double spot = request.spotPrice() != null ? request.spotPrice() : market.livePrice();
        double volatility = request.volatility() != null ? request.volatility() : market.historicalVolatility();
        double rate = request.riskFreeRate() != null ? request.riskFreeRate() : defaultRiskFreeRate;
        double timeYears = ChronoUnit.DAYS.between(LocalDate.now(), request.expiry()) / 365.0;

        double blackScholes = blackScholesService.price(request.optionType(), spot, request.strike(), timeYears, rate, volatility);
        double monteCarlo = monteCarloService.price(request.optionType(), spot, request.strike(), timeYears, rate, volatility);
        double binomial = binomialTreeService.price(request.optionType(), spot, request.strike(), timeYears, rate, volatility);
        GreeksResponse greeks = blackScholesService.greeks(request.optionType(), spot, request.strike(), timeYears, rate, volatility);
        Double impliedVolatility = request.marketPrice() == null ? null : impliedVolatilityService.impliedVolatility(
                request.optionType(), request.marketPrice(), spot, request.strike(), timeYears, rate);

        return new PricingResponse(
                ticker, spot, request.strike(), request.expiry(), timeYears, request.optionType(), rate, volatility,
                blackScholes, monteCarlo, binomial, impliedVolatility, greeks);
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

    private double modelSpread(PricingRequestLog log) {
        double highest = Math.max(log.getBlackScholesPrice(), Math.max(log.getMonteCarloPrice(), log.getBinomialTreePrice()));
        double lowest = Math.min(log.getBlackScholesPrice(), Math.min(log.getMonteCarloPrice(), log.getBinomialTreePrice()));
        return highest - lowest;
    }

    private double average(double[] values) {
        if (values.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }
}
