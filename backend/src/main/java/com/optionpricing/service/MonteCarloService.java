package com.optionpricing.service;

import com.optionpricing.model.OptionType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class MonteCarloService {
    private static final long DEFAULT_SEED = 42L;
    private final int defaultSimulations;

    public MonteCarloService(@Value("${pricing.defaults.monte-carlo-simulations:10000}") int defaultSimulations) {
        this.defaultSimulations = defaultSimulations;
    }

    public double price(OptionType type, double spot, double strike, double timeYears, double rate, double volatility) {
        return price(type, spot, strike, timeYears, rate, volatility, defaultSimulations, DEFAULT_SEED);
    }

    public double price(
            OptionType type,
            double spot,
            double strike,
            double timeYears,
            double rate,
            double volatility,
            int simulations,
            long seed
    ) {
        if (simulations <= 0) {
            throw new IllegalArgumentException("Monte Carlo simulations must be positive.");
        }

        Random random = new Random(seed);
        double drift = (rate - 0.5 * volatility * volatility) * timeYears;
        double diffusion = volatility * Math.sqrt(timeYears);
        double sum = 0.0;

        for (int i = 0; i < simulations; i++) {
            double terminalSpot = spot * Math.exp(drift + diffusion * random.nextGaussian());
            sum += type == OptionType.CALL ? Math.max(terminalSpot - strike, 0) : Math.max(strike - terminalSpot, 0);
        }

        return Math.exp(-rate * timeYears) * sum / simulations;
    }
}
