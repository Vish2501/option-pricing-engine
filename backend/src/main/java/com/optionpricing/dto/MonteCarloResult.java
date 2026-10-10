package com.optionpricing.dto;

// standardError is the uncertainty of the price estimate: the true price lies within
// about 1.96 standard errors of the estimate 95% of the time.
public record MonteCarloResult(double price, double standardError) {
}
