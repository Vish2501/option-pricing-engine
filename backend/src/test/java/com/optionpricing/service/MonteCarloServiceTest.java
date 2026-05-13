package com.optionpricing.service;

import com.optionpricing.model.OptionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class MonteCarloServiceTest {
    private final BlackScholesService blackScholes = new BlackScholesService();
    private final MonteCarloService monteCarlo = new MonteCarloService(10_000);

    @Test
    void resultIsCloseToBlackScholesButNotIdentical() {
        double expected = blackScholes.callPrice(100, 100, 1, 0.05, 0.2);
        double actual = monteCarlo.price(OptionType.CALL, 100, 100, 1, 0.05, 0.2);

        assertThat(actual).isCloseTo(expected, offset(0.5));
        assertThat(actual).isNotEqualTo(expected);
    }
}
