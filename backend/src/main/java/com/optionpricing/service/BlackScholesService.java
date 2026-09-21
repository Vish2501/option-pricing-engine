package com.optionpricing.service;

import com.optionpricing.dto.GreeksResponse;
import com.optionpricing.dto.PricingResult;
import com.optionpricing.model.OptionType;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.springframework.stereotype.Service;

/**
 * Black-Scholes-Merton pricing and Greeks for European options, including a continuous
 * dividend yield (q). Without it, prices would only be correct for non-dividend-paying
 * stocks - every formula below discounts the spot price by e^(-q*t) to account for the
 * dividends an option holder does not receive while holding the option instead of the stock.
 * <p>
 * Every formula here (price, delta, gamma, vega, theta, rho) is built from two intermediate
 * values, d1 and d2, which each method computes for itself. That recomputation is cheap
 * (a log, a sqrt, a couple of multiplications) and not worth optimizing away here - this
 * service sits behind an HTTP request that also hits Monte Carlo/binomial pricers and a
 * database write, so a handful of extra d1 calculations are noise by comparison.
 */
@Service
public class BlackScholesService {
    private final NormalDistribution normal = new NormalDistribution();

    public double price(OptionType type, double spot, double strike, double timeYears, double rate, double dividendYield, double volatility) {
        return type == OptionType.CALL
                ? callPrice(spot, strike, timeYears, rate, dividendYield, volatility)
                : putPrice(spot, strike, timeYears, rate, dividendYield, volatility);
    }

    // Standardized moneyness of the option, adjusted for drift (net of dividends) over the life of the option.
    public double d1(double spot, double strike, double timeYears, double rate, double dividendYield, double volatility) {
        validateInputs(spot, strike, timeYears, volatility);
        return (Math.log(spot / strike) + (rate - dividendYield + 0.5 * volatility * volatility) * timeYears)
                / (volatility * Math.sqrt(timeYears));
    }

    // d1 shifted by the volatility drift; used as the "risk-neutral probability of exercise" term.
    public double d2(double spot, double strike, double timeYears, double rate, double dividendYield, double volatility) {
        return d1(spot, strike, timeYears, rate, dividendYield, volatility) - volatility * Math.sqrt(timeYears);
    }

    public double callPrice(double spot, double strike, double timeYears, double rate, double dividendYield, double volatility) {
        double d1 = d1(spot, strike, timeYears, rate, dividendYield, volatility);
        double d2 = d2(spot, strike, timeYears, rate, dividendYield, volatility);
        return spot * Math.exp(-dividendYield * timeYears) * normal.cumulativeProbability(d1)
                - strike * Math.exp(-rate * timeYears) * normal.cumulativeProbability(d2);
    }

    public double putPrice(double spot, double strike, double timeYears, double rate, double dividendYield, double volatility) {
        double d1 = d1(spot, strike, timeYears, rate, dividendYield, volatility);
        double d2 = d2(spot, strike, timeYears, rate, dividendYield, volatility);
        return strike * Math.exp(-rate * timeYears) * normal.cumulativeProbability(-d2)
                - spot * Math.exp(-dividendYield * timeYears) * normal.cumulativeProbability(-d1);
    }

    public GreeksResponse greeks(OptionType type, double spot, double strike, double timeYears, double rate, double dividendYield, double volatility) {
        return new GreeksResponse(
                delta(type, spot, strike, timeYears, rate, dividendYield, volatility),
                gamma(spot, strike, timeYears, rate, dividendYield, volatility),
                vega(spot, strike, timeYears, rate, dividendYield, volatility),
                theta(type, spot, strike, timeYears, rate, dividendYield, volatility),
                rho(type, spot, strike, timeYears, rate, dividendYield, volatility)
        );
    }

    // Price and Greeks together, since OptionPricingService always wants both for the same
    // inputs - one call here instead of two separate calls at the caller.
    public PricingResult priceWithGreeks(OptionType type, double spot, double strike, double timeYears, double rate, double dividendYield, double volatility) {
        return new PricingResult(
                price(type, spot, strike, timeYears, rate, dividendYield, volatility),
                greeks(type, spot, strike, timeYears, rate, dividendYield, volatility)
        );
    }

    // Delta: sensitivity of price to a $1 move in the underlying's spot price.
    public double delta(OptionType type, double spot, double strike, double timeYears, double rate, double dividendYield, double volatility) {
        double d1 = d1(spot, strike, timeYears, rate, dividendYield, volatility);
        double dividendDiscount = Math.exp(-dividendYield * timeYears);
        return type == OptionType.CALL
                ? dividendDiscount * normal.cumulativeProbability(d1)
                : dividendDiscount * (normal.cumulativeProbability(d1) - 1);
    }

    // Gamma: sensitivity of delta itself to a $1 move in spot (same for calls and puts).
    public double gamma(double spot, double strike, double timeYears, double rate, double dividendYield, double volatility) {
        double d1 = d1(spot, strike, timeYears, rate, dividendYield, volatility);
        return Math.exp(-dividendYield * timeYears) * normal.density(d1) / (spot * volatility * Math.sqrt(timeYears));
    }

    // Vega: sensitivity of price to a 1-point change in volatility (same for calls and puts).
    public double vega(double spot, double strike, double timeYears, double rate, double dividendYield, double volatility) {
        double d1 = d1(spot, strike, timeYears, rate, dividendYield, volatility);
        return spot * Math.exp(-dividendYield * timeYears) * normal.density(d1) * Math.sqrt(timeYears);
    }

    // Theta: time decay - sensitivity of price to one year passing, held all else equal.
    public double theta(OptionType type, double spot, double strike, double timeYears, double rate, double dividendYield, double volatility) {
        double d1 = d1(spot, strike, timeYears, rate, dividendYield, volatility);
        double d2 = d2(spot, strike, timeYears, rate, dividendYield, volatility);
        double dividendDiscount = Math.exp(-dividendYield * timeYears);
        double carry = rate * strike * Math.exp(-rate * timeYears);
        double dividendCarry = dividendYield * spot * dividendDiscount;
        double diffusion = -(spot * dividendDiscount * normal.density(d1) * volatility) / (2 * Math.sqrt(timeYears));
        return type == OptionType.CALL
                ? diffusion - carry * normal.cumulativeProbability(d2) + dividendCarry * normal.cumulativeProbability(d1)
                : diffusion + carry * normal.cumulativeProbability(-d2) - dividendCarry * normal.cumulativeProbability(-d1);
    }

    // Rho: sensitivity of price to a 1-point change in the risk-free rate.
    public double rho(OptionType type, double spot, double strike, double timeYears, double rate, double dividendYield, double volatility) {
        double d2 = d2(spot, strike, timeYears, rate, dividendYield, volatility);
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
