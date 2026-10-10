package com.optionpricing.service;

import com.optionpricing.model.ExerciseStyle;
import com.optionpricing.model.OptionInputs;
import com.optionpricing.model.OptionType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BinomialTreeService {
    private final int defaultSteps;

    public BinomialTreeService(@Value("${pricing.defaults.binomial-steps:500}") int defaultSteps) {
        this.defaultSteps = defaultSteps;
    }

    public double price(OptionType type, OptionInputs inputs) {
        return price(type, ExerciseStyle.EUROPEAN, inputs, defaultSteps);
    }

    public double price(OptionType type, OptionInputs inputs, int steps) {
        return price(type, ExerciseStyle.EUROPEAN, inputs, steps);
    }

    public double price(OptionType type, ExerciseStyle style, OptionInputs inputs) {
        return price(type, style, inputs, defaultSteps);
    }

    public double price(OptionType type, ExerciseStyle style, OptionInputs inputs, int steps) {
        if (steps <= 0) {
            throw new IllegalArgumentException("Binomial steps must be positive.");
        }

        double dt = inputs.timeYears() / steps;
        double up = Math.exp(inputs.volatility() * Math.sqrt(dt));
        double down = 1.0 / up;
        double probability = (Math.exp((inputs.rate() - inputs.dividendYield()) * dt) - down) / (up - down);
        double discount = Math.exp(-inputs.rate() * dt);

        if (probability < 0 || probability > 1) {
            throw new IllegalArgumentException("Invalid risk-neutral probability for supplied inputs.");
        }

        double[] values = new double[steps + 1];
        for (int i = 0; i <= steps; i++) {
            double terminalSpot = inputs.spot() * Math.pow(up, steps - i) * Math.pow(down, i);
            values[i] = payoff(type, terminalSpot, inputs.strike());
        }

        for (int step = steps - 1; step >= 0; step--) {
            for (int i = 0; i <= step; i++) {
                values[i] = discount * (probability * values[i] + (1.0 - probability) * values[i + 1]);
                if (style == ExerciseStyle.AMERICAN) {
                    // The holder exercises now whenever that is worth more than continuing to hold.
                    double nodeSpot = inputs.spot() * Math.pow(up, step - i) * Math.pow(down, i);
                    values[i] = Math.max(values[i], payoff(type, nodeSpot, inputs.strike()));
                }
            }
        }

        return values[0];
    }

    private double payoff(OptionType type, double spot, double strike) {
        return type == OptionType.CALL ? Math.max(spot - strike, 0) : Math.max(strike - spot, 0);
    }
}
