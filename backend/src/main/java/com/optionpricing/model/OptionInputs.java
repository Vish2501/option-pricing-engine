package com.optionpricing.model;

/**
 * The numbers every pricing model needs, bundled so they travel together and cannot be
 * passed in the wrong order. Rates, yield and volatility are annual decimals (0.05 = 5%).
 */
public record OptionInputs(double spot,
                           double strike,
                           double timeYears,
                           double rate,
                           double dividendYield,
                           double volatility) {

    // A copy with a different volatility; the implied-volatility solver tries many of these.
    public OptionInputs withVolatility(double newVolatility) {
        return new OptionInputs(spot, strike, timeYears, rate, dividendYield, newVolatility);
    }
}
