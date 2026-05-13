package com.optionpricing.dto;

public record GreeksResponse(
        double delta,
        double gamma,
        double vega,
        double theta,
        double rho
) {
}
