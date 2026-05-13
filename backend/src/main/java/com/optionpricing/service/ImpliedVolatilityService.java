package com.optionpricing.service;

import com.optionpricing.model.OptionType;
import org.springframework.stereotype.Service;

@Service
public class ImpliedVolatilityService {
    private static final double TOLERANCE = 1e-6;
    private static final int MAX_ITERATIONS = 100;

    private final BlackScholesService blackScholesService;

    public ImpliedVolatilityService(BlackScholesService blackScholesService) {
        this.blackScholesService = blackScholesService;
    }

    public double impliedVolatility(OptionType type, double marketPrice, double spot, double strike, double timeYears, double rate) {
        double volatility = newtonRaphson(type, marketPrice, spot, strike, timeYears, rate);
        if (Double.isFinite(volatility) && volatility > 0) {
            return volatility;
        }
        return bisection(type, marketPrice, spot, strike, timeYears, rate);
    }

    private double newtonRaphson(OptionType type, double marketPrice, double spot, double strike, double timeYears, double rate) {
        double volatility = 0.2;
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            double price = blackScholesService.price(type, spot, strike, timeYears, rate, volatility);
            double diff = price - marketPrice;
            if (Math.abs(diff) < TOLERANCE) {
                return volatility;
            }

            double annualVega = blackScholesService.vega(spot, strike, timeYears, rate, volatility);
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

    private double bisection(OptionType type, double marketPrice, double spot, double strike, double timeYears, double rate) {
        double low = 1e-6;
        double high = 5.0;
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            double mid = (low + high) / 2.0;
            double price = blackScholesService.price(type, spot, strike, timeYears, rate, mid);
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
