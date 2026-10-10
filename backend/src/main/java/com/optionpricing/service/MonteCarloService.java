package com.optionpricing.service;

import com.optionpricing.dto.MonteCarloResult;
import com.optionpricing.model.OptionInputs;
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

    public double price(OptionType type, OptionInputs inputs) {
        return simulate(type, inputs).price();
    }

    public double price(OptionType type, OptionInputs inputs, int simulations, long seed) {
        return simulate(type, inputs, simulations, seed).price();
    }

    public MonteCarloResult simulate(OptionType type, OptionInputs inputs) {
        return simulate(type, inputs, defaultSimulations, DEFAULT_SEED);
    }

    public MonteCarloResult simulate(OptionType type, OptionInputs inputs, int simulations, long seed) {
        if (simulations <= 0) {
            throw new IllegalArgumentException("Monte Carlo simulations must be positive.");
        }

        Random random = new Random(seed);
        double drift = (inputs.rate() - inputs.dividendYield() - 0.5 * inputs.volatility() * inputs.volatility()) * inputs.timeYears();
        double diffusion = inputs.volatility() * Math.sqrt(inputs.timeYears());
        double sum = 0.0;
        double sumOfSquares = 0.0;

        for (int i = 0; i < simulations; i++) {
            double terminalSpot = inputs.spot() * Math.exp(drift + diffusion * random.nextGaussian());
            double payoff = type == OptionType.CALL ? Math.max(terminalSpot - inputs.strike(), 0) : Math.max(inputs.strike() - terminalSpot, 0);
            sum += payoff;
            sumOfSquares += payoff * payoff;
        }

        double discount = Math.exp(-inputs.rate() * inputs.timeYears());
        double mean = sum / simulations;
        // Sample variance of the payoffs; the estimate's standard error shrinks with the square root of the path count.
        double variance = simulations > 1 ? Math.max(sumOfSquares - simulations * mean * mean, 0) / (simulations - 1) : 0.0;
        return new MonteCarloResult(discount * mean, discount * Math.sqrt(variance / simulations));
    }
}
