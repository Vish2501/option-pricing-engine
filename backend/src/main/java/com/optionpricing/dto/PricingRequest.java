package com.optionpricing.dto;

import com.optionpricing.model.OptionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PricingRequest(
        @NotBlank @Size(max = 12) @Pattern(regexp = "^[A-Za-z0-9.\\-]+$", message = "must be a valid market ticker") String ticker,
        @DecimalMin(value = "0.0", inclusive = false) Double spotPrice,
        @DecimalMin(value = "0.0", inclusive = false) double strike,
        @NotNull @Future LocalDate expiry,
        @NotNull OptionType optionType,
        @DecimalMin(value = "0.0", inclusive = false) Double volatility,
        Double riskFreeRate,
        @DecimalMin(value = "0.0", inclusive = false) Double marketPrice
) {
}
