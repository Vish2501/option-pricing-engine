package com.optionpricing.service;

import com.optionpricing.model.OptionType;
import org.springframework.stereotype.Service;

import java.util.OptionalDouble;

@Service
public class ImpliedVolatilityService {
    private static final double TOLERANCE = 1e-6;
    private static final int MAX_ITERATIONS = 100;
    private static final double REPRICE_TOLERANCE = 1e-4;

    private final BlackScholesService blackScholesService;

    public ImpliedVolatilityService(BlackScholesService blackScholesService) {
        this.blackScholesService = blackScholesService;
    }

    /**
     * Solves for the volatility at which Black-Scholes reproduces the market price. Returns
     * empty when no volatility can: a price at or below the option's value at zero volatility,
     * or at or above its value at infinite volatility, would be an arbitrage.
     */
    public OptionalDouble impliedVolatility(OptionType type, double marketPrice, double spot, double strike, double timeYears, double rate, double dividendYield) {
        double discountedSpot = spot * Math.exp(-dividendYield * timeYears);
        double discountedStrike = strike * Math.exp(-rate * timeYears);
        double lowerBound = type == OptionType.CALL
                ? Math.max(discountedSpot - discountedStrike, 0)
                : Math.max(discountedStrike - discountedSpot, 0);
        double upperBound = type == OptionType.CALL ? discountedSpot : discountedStrike;
        if (marketPrice <= lowerBound || marketPrice >= upperBound) {
            return OptionalDouble.empty();
        }

        double volatility = newtonRaphson(type, marketPrice, spot, strike, timeYears, rate, dividendYield);
        if (!Double.isFinite(volatility) || volatility <= 0) {
            volatility = bisection(type, marketPrice, spot, strike, timeYears, rate, dividendYield);
        }

        // The search is capped at 500% volatility; a price that needs more than that has no usable answer.
        double repriced = blackScholesService.price(type, spot, strike, timeYears, rate, dividendYield, volatility);
        return Math.abs(repriced - marketPrice) < REPRICE_TOLERANCE ? OptionalDouble.of(volatility) : OptionalDouble.empty();
    }

    private double newtonRaphson(OptionType type, double marketPrice, double spot, double strike, double timeYears, double rate, double dividendYield) {
        double volatility = 0.2;
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            double price = blackScholesService.price(type, spot, strike, timeYears, rate, dividendYield, volatility);
            double diff = price - marketPrice;
            if (Math.abs(diff) < TOLERANCE) {
                return volatility;
            }

            double annualVega = blackScholesService.vega(spot, strike, timeYears, rate, dividendYield, volatility);
            if (Math.abs(annualVega) < 1e-8) {
                return Double.NaN;
            }

            volatility -= diff / annualVega;
            if (volatility <= 0 || volatility > 5) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    private double bisection(OptionType type, double marketPrice, double spot, double strike, double timeYears, double rate, double dividendYield) {
        double low = 1e-6;
        double high = 5.0;
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            double mid = (low + high) / 2.0;
            double price = blackScholesService.price(type, spot, strike, timeYears, rate, dividendYield, mid);
            if (Math.abs(price - marketPrice) < TOLERANCE) {
                return mid;
            }
            if (price > marketPrice) {
                high = mid;
            } else {
                low = mid;
            }
        }
        return (low + high) / 2.0;
    }
}
