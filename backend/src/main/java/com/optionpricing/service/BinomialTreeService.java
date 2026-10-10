package com.optionpricing.service;

import com.optionpricing.model.ExerciseStyle;
import com.optionpricing.model.OptionType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BinomialTreeService {
    private final int defaultSteps;

    public BinomialTreeService(@Value("${pricing.defaults.binomial-steps:500}") int defaultSteps) {
        this.defaultSteps = defaultSteps;
    }

    public double price(OptionType type, double spot, double strike, double timeYears, double rate, double dividendYield, double volatility) {
        return price(type, ExerciseStyle.EUROPEAN, spot, strike, timeYears, rate, dividendYield, volatility, defaultSteps);
    }

    public double price(OptionType type, double spot, double strike, double timeYears, double rate, double dividendYield, double volatility, int steps) {
        return price(type, ExerciseStyle.EUROPEAN, spot, strike, timeYears, rate, dividendYield, volatility, steps);
    }

    public double price(OptionType type, ExerciseStyle style, double spot, double strike, double timeYears, double rate, double dividendYield, double volatility) {
        return price(type, style, spot, strike, timeYears, rate, dividendYield, volatility, defaultSteps);
    }

    public double price(OptionType type, ExerciseStyle style, double spot, double strike, double timeYears, double rate, double dividendYield, double volatility, int steps) {
        if (steps <= 0) {
            throw new IllegalArgumentException("Binomial steps must be positive.");
        }

        double dt = timeYears / steps;
        double up = Math.exp(volatility * Math.sqrt(dt));
        double down = 1.0 / up;
        double probability = (Math.exp((rate - dividendYield) * dt) - down) / (up - down);
        double discount = Math.exp(-rate * dt);

        if (probability < 0 || probability > 1) {
            throw new IllegalArgumentException("Invalid risk-neutral probability for supplied inputs.");
        }

        double[] values = new double[steps + 1];
        for (int i = 0; i <= steps; i++) {
            double terminalSpot = spot * Math.pow(up, steps - i) * Math.pow(down, i);
            values[i] = payoff(type, terminalSpot, strike);
        }

        for (int step = steps - 1; step >= 0; step--) {
            for (int i = 0; i <= step; i++) {
                values[i] = discount * (probability * values[i] + (1.0 - probability) * values[i + 1]);
                if (style == ExerciseStyle.AMERICAN) {
                    // The holder exercises now whenever that is worth more than continuing to hold.
                    double nodeSpot = spot * Math.pow(up, step - i) * Math.pow(down, i);
                    values[i] = Math.max(values[i], payoff(type, nodeSpot, strike));
                }
            }
        }

        return values[0];
    }

    private double payoff(OptionType type, double spot, double strike) {
        return type == OptionType.CALL ? Math.max(spot - strike, 0) : Math.max(strike - spot, 0);
    }
}
