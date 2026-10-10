package com.optionpricing.service;

import com.optionpricing.model.OptionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class BinomialTreeServiceTest {
    private final BlackScholesService blackScholes = new BlackScholesService();
    private final BinomialTreeService binomialTree = new BinomialTreeService(500);

    @Test
    void europeanCallConvergesToBlackScholes() {
        double expected = blackScholes.callPrice(100, 100, 1, 0.05, 0.0, 0.2);
        double actual = binomialTree.price(OptionType.CALL, 100, 100, 1, 0.05, 0.0, 0.2, 1000);

        assertThat(actual).isCloseTo(expected, offset(0.02));
    }

    @Test
    void europeanPutConvergesToBlackScholes() {
        double expected = blackScholes.putPrice(100, 100, 1, 0.05, 0.0, 0.2);
        double actual = binomialTree.price(OptionType.PUT, 100, 100, 1, 0.05, 0.0, 0.2, 1000);

        assertThat(actual).isCloseTo(expected, offset(0.02));
    }
    @Test
    void europeanCallWithDividendYieldConvergesToBlackScholes(){
        double spot = 100;
        double strike = 90;
        double timeYears = 1;
        double rate = 0.05;
        double dividendYield = 0.03;
        double volatility = 0.2;

        double expected = blackScholes.callPrice(100,90,1,0.05,0.03,0.2);
        double actual = binomialTree.price(OptionType.CALL, spot, strike, timeYears, rate, dividendYield, volatility, 1000);
        assertThat(actual).isCloseTo(expected, offset(0.02));
    }
}
