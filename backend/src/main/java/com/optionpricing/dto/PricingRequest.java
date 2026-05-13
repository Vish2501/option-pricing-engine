package com.optionpricing.dto;

import com.optionpricing.model.OptionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record PricingRequest(
        @NotBlank String ticker,
        @DecimalMin(value = "0.0", inclusive = false) Double spotPrice,
        @DecimalMin(value = "0.0", inclusive = false) double strike,
        @NotNull @Future LocalDate expiry,
        @NotNull OptionType optionType,
        @DecimalMin(value = "0.0", inclusive = false) Double volatility,
        Double riskFreeRate,
        @DecimalMin(value = "0.0", inclusive = false) Double marketPrice
) {
}
