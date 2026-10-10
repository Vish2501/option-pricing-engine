package com.optionpricing.dto;

import com.optionpricing.model.OptionType;
import com.optionpricing.model.TickerSymbol;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record PricingRequest(
        @NotBlank @Pattern(regexp = TickerSymbol.PATTERN, message = "must be a valid market ticker") String ticker,
        @DecimalMin(value = "0.0", inclusive = false) Double spotPrice,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) Double strike,
        @NotNull @Future LocalDate expiry,
        @NotNull OptionType optionType,
        @DecimalMin(value = "0.0", inclusive = false) Double volatility,
        Double riskFreeRate,
        @DecimalMin(value = "0.0", inclusive = false) Double marketPrice,
        @DecimalMin(value = "0.0") Double dividendYield
) {
}
