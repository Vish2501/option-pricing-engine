package com.optionpricing.service;

import com.optionpricing.model.OptionInputs;
import com.optionpricing.model.OptionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class ImpliedVolatilityServiceTest {
    private final BlackScholesService blackScholes = new BlackScholesService();
    private final ImpliedVolatilityService service = new ImpliedVolatilityService(blackScholes);
    // The solver ignores the volatility in its inputs - that is what it solves for.
    private static final double UNUSED = Double.NaN;

    @Test
    void backSolvesPythonProjectSampleImpliedVolatility() {
        double impliedVolatility = service.impliedVolatility(
                OptionType.CALL, 22.25, new OptionInputs(226.5, 207.5, 7.0 / 365.0, 0.05, 0.0, UNUSED)).orElseThrow();

        assertThat(impliedVolatility).isCloseTo(0.8216, offset(0.01));
    }

    // Price an option at a known volatility, then check the solver recovers that volatility.
    @ParameterizedTest
    @CsvSource({
            "CALL, 100, 100, 1.0, 0.05, 0.00, 0.10",
            "CALL, 100, 120, 0.5, 0.03, 0.02, 0.35",
            "CALL, 100,  80, 2.0, 0.01, 0.00, 0.90",
            "PUT,  100, 100, 1.0, 0.05, 0.00, 0.10",
            "PUT,  100,  80, 0.5, 0.03, 0.02, 0.35",
            "PUT,  100, 120, 2.0, 0.01, 0.00, 0.90"
    })
    void recoversTheVolatilityAnOptionWasPricedAt(OptionType type, double spot, double strike, double timeYears,
                                                  double rate, double dividendYield, double volatility) {
        OptionInputs inputs = new OptionInputs(spot, strike, timeYears, rate, dividendYield, volatility);
        double price = blackScholes.price(type, inputs);

        assertThat(service.impliedVolatility(type, price, inputs).orElseThrow())
                .isCloseTo(volatility, offset(1e-4));
    }

    @Test
    void callPricedBelowItsExerciseValueHasNoImpliedVolatility() {
        // Spot 120, strike 100: the call is worth at least about 20 whatever the volatility.
        assertThat(service.impliedVolatility(OptionType.CALL, 15, new OptionInputs(120, 100, 0.5, 0.05, 0.0, UNUSED))).isEmpty();
    }

    @Test
    void callPricedAboveTheStockHasNoImpliedVolatility() {
        assertThat(service.impliedVolatility(OptionType.CALL, 101, new OptionInputs(100, 100, 0.5, 0.05, 0.0, UNUSED))).isEmpty();
    }

    @Test
    void putPricedAboveItsDiscountedStrikeHasNoImpliedVolatility() {
        assertThat(service.impliedVolatility(OptionType.PUT, 100, new OptionInputs(100, 100, 1, 0.05, 0.0, UNUSED))).isEmpty();
    }
}
