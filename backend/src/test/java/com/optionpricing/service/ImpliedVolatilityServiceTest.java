package com.optionpricing.service;

import com.optionpricing.model.OptionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class ImpliedVolatilityServiceTest {
    private final BlackScholesService blackScholes = new BlackScholesService();
    private final ImpliedVolatilityService service = new ImpliedVolatilityService(blackScholes);

    @Test
    void backSolvesPythonProjectSampleImpliedVolatility() {
        double impliedVolatility = service.impliedVolatility(
                OptionType.CALL, 22.25, 226.5, 207.5, 7.0 / 365.0, 0.05);

        assertThat(impliedVolatility).isCloseTo(0.8216, offset(0.01));
    }
}
