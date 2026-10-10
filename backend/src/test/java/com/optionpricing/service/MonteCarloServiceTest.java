package com.optionpricing.service;

import com.optionpricing.dto.MonteCarloResult;
import com.optionpricing.model.OptionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class MonteCarloServiceTest {
    private final BlackScholesService blackScholes = new BlackScholesService();
    private final MonteCarloService monteCarlo = new MonteCarloService(10_000);

    @Test
    void resultIsCloseToBlackScholesButNotIdentical() {
        double expected = blackScholes.callPrice(100, 100, 1, 0.05, 0.0, 0.2);
        double actual = monteCarlo.price(OptionType.CALL, 100, 100, 1, 0.05, 0.0, 0.2);

        assertThat(actual).isCloseTo(expected, offset(0.5));
        assertThat(actual).isNotEqualTo(expected);
    }

    @Test
    void blackScholesPriceLiesWithinThreeStandardErrors() {
        double expected = blackScholes.putPrice(100, 105, 0.5, 0.03, 0.01, 0.3);
        MonteCarloResult result = monteCarlo.simulate(OptionType.PUT, 100, 105, 0.5, 0.03, 0.01, 0.3);

        assertThat(result.standardError()).isPositive();
        assertThat(Math.abs(result.price() - expected)).isLessThan(3 * result.standardError());
    }

    @Test
    void standardErrorHalvesWhenThePathCountQuadruples() {
        double base = monteCarlo.simulate(OptionType.CALL, 100, 100, 1, 0.05, 0.0, 0.2, 10_000, 7L).standardError();
        double quadrupled = monteCarlo.simulate(OptionType.CALL, 100, 100, 1, 0.05, 0.0, 0.2, 40_000, 7L).standardError();

        assertThat(quadrupled / base).isCloseTo(0.5, offset(0.03));
    }
}
