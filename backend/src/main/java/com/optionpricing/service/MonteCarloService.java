package com.optionpricing.service;

import com.optionpricing.dto.MonteCarloResult;
import com.optionpricing.model.OptionType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class MonteCarloService {
    // A fixed seed makes the same inputs always return the same price, which keeps results
    // reproducible and testable. The standard error reports how far off that one draw could be.
    private static final long DEFAULT_SEED = 42L;
    private final int defaultSimulations;

    public MonteCarloService(@Value("${pricing.defaults.monte-carlo-simulations:10000}") int defaultSimulations) {
        this.defaultSimulations = defaultSimulations;
    }

    public double price(OptionType type, double spot, double strike, double timeYears, double rate, double dividendYield, double volatility) {
        return price(type, spot, strike, timeYears, rate, dividendYield, volatility, defaultSimulations, DEFAULT_SEED);
    }

    public double price(OptionType type, double spot, double strike, double timeYears, double rate, double dividendYield, double volatility, int simulations, long seed) {
        return simulate(type, spot, strike, timeYears, rate, dividendYield, volatility, simulations, seed).price();
    }

    public MonteCarloResult simulate(OptionType type, double spot, double strike, double timeYears, double rate, double dividendYield, double volatility) {
        return simulate(type, spot, strike, timeYears, rate, dividendYield, volatility, defaultSimulations, DEFAULT_SEED);
    }

    public MonteCarloResult simulate(
            OptionType type,
            double spot,
            double strike,
            double timeYears,
            double rate,
            double dividendYield,
            double volatility,
            int simulations,
            long seed
    ) {
        if (simulations <= 0) {
            throw new IllegalArgumentException("Monte Carlo simulations must be positive.");
        }

        Random random = new Random(seed);
        double drift = (rate - dividendYield - 0.5 * volatility * volatility) * timeYears;
        double diffusion = volatility * Math.sqrt(timeYears);
        double sum = 0.0;
        double sumOfSquares = 0.0;

        for (int i = 0; i < simulations; i++) {
            double terminalSpot = spot * Math.exp(drift + diffusion * random.nextGaussian());
            double payoff = type == OptionType.CALL ? Math.max(terminalSpot - strike, 0) : Math.max(strike - terminalSpot, 0);
            sum += payoff;
            sumOfSquares += payoff * payoff;
        }

        double discount = Math.exp(-rate * timeYears);
        double mean = sum / simulations;
        // Sample variance of the payoffs; the estimate's standard error shrinks with the square root of the path count.
        double variance = simulations > 1 ? Math.max(sumOfSquares - simulations * mean * mean, 0) / (simulations - 1) : 0.0;
        return new MonteCarloResult(discount * mean, discount * Math.sqrt(variance / simulations));
    }
}
