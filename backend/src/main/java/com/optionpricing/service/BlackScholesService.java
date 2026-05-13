package com.optionpricing.service;

import com.optionpricing.dto.GreeksResponse;
import com.optionpricing.model.OptionType;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.springframework.stereotype.Service;

@Service
public class BlackScholesService {
    private final NormalDistribution normal = new NormalDistribution();

    public double d1(double spot, double strike, double timeYears, double rate, double volatility) {
        validateInputs(spot, strike, timeYears, volatility);
        return (Math.log(spot / strike) + (rate + 0.5 * volatility * volatility) * timeYears)
                / (volatility * Math.sqrt(timeYears));
    }

    public double d2(double spot, double strike, double timeYears, double rate, double volatility) {
        return d1(spot, strike, timeYears, rate, volatility) - volatility * Math.sqrt(timeYears);
    }

    public double price(OptionType type, double spot, double strike, double timeYears, double rate, double volatility) {
        return type == OptionType.CALL
                ? callPrice(spot, strike, timeYears, rate, volatility)
                : putPrice(spot, strike, timeYears, rate, volatility);
    }

    public double callPrice(double spot, double strike, double timeYears, double rate, double volatility) {
        double d1 = d1(spot, strike, timeYears, rate, volatility);
        double d2 = d2(spot, strike, timeYears, rate, volatility);
        return spot * normal.cumulativeProbability(d1)
                - strike * Math.exp(-rate * timeYears) * normal.cumulativeProbability(d2);
    }

    public double putPrice(double spot, double strike, double timeYears, double rate, double volatility) {
        double d1 = d1(spot, strike, timeYears, rate, volatility);
        double d2 = d2(spot, strike, timeYears, rate, volatility);
        return strike * Math.exp(-rate * timeYears) * normal.cumulativeProbability(-d2)
                - spot * normal.cumulativeProbability(-d1);
    }

    public GreeksResponse greeks(OptionType type, double spot, double strike, double timeYears, double rate, double volatility) {
        return new GreeksResponse(
                delta(type, spot, strike, timeYears, rate, volatility),
                gamma(spot, strike, timeYears, rate, volatility),
                vega(spot, strike, timeYears, rate, volatility),
                theta(type, spot, strike, timeYears, rate, volatility),
                rho(type, spot, strike, timeYears, rate, volatility)
        );
    }

    public double delta(OptionType type, double spot, double strike, double timeYears, double rate, double volatility) {
        double d1 = d1(spot, strike, timeYears, rate, volatility);
        return type == OptionType.CALL ? normal.cumulativeProbability(d1) : normal.cumulativeProbability(d1) - 1;
    }

    public double gamma(double spot, double strike, double timeYears, double rate, double volatility) {
        double d1 = d1(spot, strike, timeYears, rate, volatility);
        return normal.density(d1) / (spot * volatility * Math.sqrt(timeYears));
    }

    public double vega(double spot, double strike, double timeYears, double rate, double volatility) {
        double d1 = d1(spot, strike, timeYears, rate, volatility);
        return spot * normal.density(d1) * Math.sqrt(timeYears);
    }

    public double theta(OptionType type, double spot, double strike, double timeYears, double rate, double volatility) {
        double d1 = d1(spot, strike, timeYears, rate, volatility);
        double d2 = d2(spot, strike, timeYears, rate, volatility);
        double carry = rate * strike * Math.exp(-rate * timeYears);
        double diffusion = -(spot * normal.density(d1) * volatility) / (2 * Math.sqrt(timeYears));
        double annual = type == OptionType.CALL
                ? diffusion - carry * normal.cumulativeProbability(d2)
                : diffusion + carry * normal.cumulativeProbability(-d2);
        return annual;
    }

    public double rho(OptionType type, double spot, double strike, double timeYears, double rate, double volatility) {
        double d2 = d2(spot, strike, timeYears, rate, volatility);
        double annual = strike * timeYears * Math.exp(-rate * timeYears);
        return type == OptionType.CALL
                ? annual * normal.cumulativeProbability(d2)
                : -annual * normal.cumulativeProbability(-d2);
    }

    private void validateInputs(double spot, double strike, double timeYears, double volatility) {
        if (spot <= 0 || strike <= 0 || timeYears <= 0 || volatility <= 0) {
            throw new IllegalArgumentException("Spot, strike, time to maturity, and volatility must be positive.");
        }
    }
}
