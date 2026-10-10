package com.optionpricing.service;

import com.optionpricing.dto.GreeksResponse;
import com.optionpricing.dto.PricingResult;
import com.optionpricing.model.OptionInputs;
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

    public double price(OptionType type, OptionInputs inputs) {
        return type == OptionType.CALL
                ? callPrice(inputs)
                : putPrice(inputs);
    }

    // Standardized moneyness of the option, adjusted for drift (net of dividends) over the life of the option.
    public double d1(OptionInputs inputs) {
        validateInputs(inputs);
        return (Math.log(inputs.spot() / inputs.strike()) + (inputs.rate() - inputs.dividendYield() + 0.5 * inputs.volatility() * inputs.volatility()) * inputs.timeYears())
                / (inputs.volatility() * Math.sqrt(inputs.timeYears()));
    }

    // d1 shifted by the volatility drift; used as the "risk-neutral probability of exercise" term.
    public double d2(OptionInputs inputs) {
        return d1(inputs) - inputs.volatility() * Math.sqrt(inputs.timeYears());
    }

    public double callPrice(OptionInputs inputs) {
        double d1 = d1(inputs);
        double d2 = d2(inputs);
        return inputs.spot() * Math.exp(-inputs.dividendYield() * inputs.timeYears()) * normal.cumulativeProbability(d1)
                - inputs.strike() * Math.exp(-inputs.rate() * inputs.timeYears()) * normal.cumulativeProbability(d2);
    }

    public double putPrice(OptionInputs inputs) {
        double d1 = d1(inputs);
        double d2 = d2(inputs);
        return inputs.strike() * Math.exp(-inputs.rate() * inputs.timeYears()) * normal.cumulativeProbability(-d2)
                - inputs.spot() * Math.exp(-inputs.dividendYield() * inputs.timeYears()) * normal.cumulativeProbability(-d1);
    }

    public GreeksResponse greeks(OptionType type, OptionInputs inputs) {
        return new GreeksResponse(
                delta(type, inputs),
                gamma(inputs),
                vega(inputs),
                theta(type, inputs),
                rho(type, inputs)
        );
    }

    // Price and Greeks together, since OptionPricingService always wants both for the same
    // inputs - one call here instead of two separate calls at the caller.
    public PricingResult priceWithGreeks(OptionType type, OptionInputs inputs) {
        return new PricingResult(
                price(type, inputs),
                greeks(type, inputs)
        );
    }

    // Delta: sensitivity of price to a $1 move in the underlying's spot price.
    public double delta(OptionType type, OptionInputs inputs) {
        double d1 = d1(inputs);
        double dividendDiscount = Math.exp(-inputs.dividendYield() * inputs.timeYears());
        return type == OptionType.CALL
                ? dividendDiscount * normal.cumulativeProbability(d1)
                : dividendDiscount * (normal.cumulativeProbability(d1) - 1);
    }

    // Gamma: sensitivity of delta itself to a $1 move in spot (same for calls and puts).
    public double gamma(OptionInputs inputs) {
        double d1 = d1(inputs);
        return Math.exp(-inputs.dividendYield() * inputs.timeYears()) * normal.density(d1) / (inputs.spot() * inputs.volatility() * Math.sqrt(inputs.timeYears()));
    }

    // Vega: sensitivity of price to a 1-point change in volatility (same for calls and puts).
    public double vega(OptionInputs inputs) {
        double d1 = d1(inputs);
        return inputs.spot() * Math.exp(-inputs.dividendYield() * inputs.timeYears()) * normal.density(d1) * Math.sqrt(inputs.timeYears());
    }

    // Theta: time decay - sensitivity of price to one year passing, held all else equal.
    public double theta(OptionType type, OptionInputs inputs) {
        double d1 = d1(inputs);
        double d2 = d2(inputs);
        double dividendDiscount = Math.exp(-inputs.dividendYield() * inputs.timeYears());
        double carry = inputs.rate() * inputs.strike() * Math.exp(-inputs.rate() * inputs.timeYears());
        double dividendCarry = inputs.dividendYield() * inputs.spot() * dividendDiscount;
        double diffusion = -(inputs.spot() * dividendDiscount * normal.density(d1) * inputs.volatility()) / (2 * Math.sqrt(inputs.timeYears()));
        return type == OptionType.CALL
                ? diffusion - carry * normal.cumulativeProbability(d2) + dividendCarry * normal.cumulativeProbability(d1)
                : diffusion + carry * normal.cumulativeProbability(-d2) - dividendCarry * normal.cumulativeProbability(-d1);
    }

    // Rho: sensitivity of price to a 1-point change in the risk-free rate.
    public double rho(OptionType type, OptionInputs inputs) {
        double d2 = d2(inputs);
        double annual = inputs.strike() * inputs.timeYears() * Math.exp(-inputs.rate() * inputs.timeYears());
        return type == OptionType.CALL
                ? annual * normal.cumulativeProbability(d2)
                : -annual * normal.cumulativeProbability(-d2);
    }

    private void validateInputs(OptionInputs inputs) {
        if (inputs.spot() <= 0 || inputs.strike() <= 0 || inputs.timeYears() <= 0 || inputs.volatility() <= 0) {
            throw new IllegalArgumentException("Spot, strike, time to maturity, and volatility must be positive.");
        }
    }
}
