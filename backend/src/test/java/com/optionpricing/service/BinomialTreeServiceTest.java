package com.optionpricing.service;

import com.optionpricing.model.ExerciseStyle;
import com.optionpricing.model.OptionInputs;
import com.optionpricing.model.OptionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class BinomialTreeServiceTest {
    private final BlackScholesService blackScholes = new BlackScholesService();
    private final BinomialTreeService binomialTree = new BinomialTreeService(500);

    @Test
    void europeanCallConvergesToBlackScholes() {
        double expected = blackScholes.callPrice(new OptionInputs(100, 100, 1, 0.05, 0.0, 0.2));
        double actual = binomialTree.price(OptionType.CALL, new OptionInputs(100, 100, 1, 0.05, 0.0, 0.2), 1000);

        assertThat(actual).isCloseTo(expected, offset(0.02));
    }

    @Test
    void europeanPutConvergesToBlackScholes() {
        double expected = blackScholes.putPrice(new OptionInputs(100, 100, 1, 0.05, 0.0, 0.2));
        double actual = binomialTree.price(OptionType.PUT, new OptionInputs(100, 100, 1, 0.05, 0.0, 0.2), 1000);

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

        double expected = blackScholes.callPrice(new OptionInputs(100, 90, 1, 0.05, 0.03, 0.2));
        double actual = binomialTree.price(OptionType.CALL, new OptionInputs(spot, strike, timeYears, rate, dividendYield, volatility), 1000);
        assertThat(actual).isCloseTo(expected, offset(0.02));
    }

    @Test
    void americanCallWithoutDividendsIsWorthTheSameAsEuropean() {
        // Exercising a call early gives up time value and earns nothing in return when there are no dividends.
        double european = binomialTree.price(OptionType.CALL, ExerciseStyle.EUROPEAN, new OptionInputs(100, 100, 1, 0.05, 0.0, 0.2), 1000);
        double american = binomialTree.price(OptionType.CALL, ExerciseStyle.AMERICAN, new OptionInputs(100, 100, 1, 0.05, 0.0, 0.2), 1000);

        assertThat(american).isCloseTo(european, offset(1e-9));
    }

    @Test
    void americanPutIsWorthMoreThanEuropeanAndMatchesTheTextbookValue() {
        double european = binomialTree.price(OptionType.PUT, ExerciseStyle.EUROPEAN, new OptionInputs(100, 100, 1, 0.05, 0.0, 0.2), 1000);
        double american = binomialTree.price(OptionType.PUT, ExerciseStyle.AMERICAN, new OptionInputs(100, 100, 1, 0.05, 0.0, 0.2), 1000);

        assertThat(american).isGreaterThan(european);
        assertThat(american).isCloseTo(6.09, offset(0.01));
    }

    @Test
    void americanCallWithDividendsCarriesAnEarlyExercisePremium() {
        double european = binomialTree.price(OptionType.CALL, ExerciseStyle.EUROPEAN, new OptionInputs(100, 90, 1, 0.05, 0.08, 0.2), 1000);
        double american = binomialTree.price(OptionType.CALL, ExerciseStyle.AMERICAN, new OptionInputs(100, 90, 1, 0.05, 0.08, 0.2), 1000);

        assertThat(american).isGreaterThan(european);
    }

    @Test
    void americanOptionIsNeverWorthLessThanExercisingImmediately() {
        double american = binomialTree.price(OptionType.PUT, ExerciseStyle.AMERICAN, new OptionInputs(80, 100, 1, 0.05, 0.0, 0.2), 1000);

        assertThat(american).isGreaterThanOrEqualTo(20.0);
    }
}
