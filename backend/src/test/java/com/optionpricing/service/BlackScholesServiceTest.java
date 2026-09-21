package com.optionpricing.service;

import com.optionpricing.dto.GreeksResponse;
import com.optionpricing.model.OptionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class BlackScholesServiceTest {
    private final BlackScholesService service = new BlackScholesService();

    @Test
    void matchesPythonProjectSampleReportValues() {
        double spot = 226.5;
        double strike = 207.5;
        double timeYears = 7.0 / 365.0;
        double rate = 0.05;
        double volatility = 0.2191;

        assertThat(service.price(OptionType.CALL, spot, strike, timeYears, rate, 0.0, volatility))
                .isCloseTo(19.17, offset(0.04));

        GreeksResponse greeks = service.greeks(OptionType.CALL, spot, strike, timeYears, rate, 0.0, volatility);
        assertThat(greeks.delta()).isCloseTo(0.9983, offset(0.001));
        assertThat(greeks.gamma()).isCloseTo(0.0008, offset(0.0005));
        assertThat(greeks.vega()).isCloseTo(0.1710, offset(0.005));
        assertThat(greeks.theta()).isCloseTo(-11.3226, offset(0.2));
        assertThat(greeks.rho()).isCloseTo(3.9682, offset(0.05));
    }

    @Test
    void calculatesKnownOneYearAtTheMoneyValues() {
        assertThat(service.d1(100, 100, 1, 0.05, 0.0, 0.2)).isCloseTo(0.35, offset(1e-10));
        assertThat(service.d2(100, 100, 1, 0.05, 0.0, 0.2)).isCloseTo(0.15, offset(1e-10));
        assertThat(service.callPrice(100, 100, 1, 0.05, 0.0, 0.2)).isCloseTo(10.4506, offset(0.0001));
        assertThat(service.putPrice(100, 100, 1, 0.05, 0.0, 0.2)).isCloseTo(5.5735, offset(0.0001));
    }

    @Test
    void higherDividendYieldLowersCallPriceAndRaisesPutPrice() {
        double spot = 100;
        double strike = 100;
        double timeYears = 1;
        double rate = 0.05;
        double volatility = 0.2;

        double callNoDividend = service.callPrice(spot, strike, timeYears, rate, 0.0, volatility);
        double callWithDividend = service.callPrice(spot, strike, timeYears, rate, 0.03, volatility);
        double putNoDividend = service.putPrice(spot, strike, timeYears, rate, 0.0, volatility);
        double putWithDividend = service.putPrice(spot, strike, timeYears, rate, 0.03, volatility);

        assertThat(callWithDividend).isLessThan(callNoDividend);
        assertThat(putWithDividend).isGreaterThan(putNoDividend);
    }

    @Test
    void satisfiesPutCallParityWithDividendYield() {
        double spot = 100;
        double strike = 95;
        double timeYears = 0.5;
        double rate = 0.04;
        double dividendYield = 0.02;
        double volatility = 0.25;

        double call = service.callPrice(spot, strike, timeYears, rate, dividendYield, volatility);
        double put = service.putPrice(spot, strike, timeYears, rate, dividendYield, volatility);
        double expectedDifference = spot * Math.exp(-dividendYield * timeYears) - strike * Math.exp(-rate * timeYears);

        assertThat(call - put).isCloseTo(expectedDifference, offset(1e-9));
    }
}
