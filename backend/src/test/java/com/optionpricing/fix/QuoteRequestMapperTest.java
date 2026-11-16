package com.optionpricing.fix;

import com.optionpricing.dto.PricingRequest;
import com.optionpricing.fix.QuoteRequestMapper.MappedQuoteRequest;
import com.optionpricing.model.OptionType;
import org.junit.jupiter.api.Test;
import quickfix.field.MaturityDate;
import quickfix.field.PutOrCall;
import quickfix.field.QuoteReqID;
import quickfix.field.QuoteRequestRejectReason;
import quickfix.field.SecurityType;
import quickfix.field.StrikePrice;
import quickfix.field.Symbol;
import quickfix.fix44.QuoteRequest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuoteRequestMapperTest {

    @Test
    void mapsEveryFieldOfAValidRequest() {
        QuoteRequest message = request(callOption());
        message.setString(PricingModel.FIX_TAG, "MC");

        MappedQuoteRequest mapped = QuoteRequestMapper.map(message);

        PricingRequest pricing = mapped.pricingRequest();
        assertEquals("AAPL", pricing.ticker());
        assertEquals(207.5, pricing.strike());
        assertEquals(LocalDate.of(2099, 6, 18), pricing.expiry());
        assertEquals(OptionType.CALL, pricing.optionType());
        assertEquals(PricingModel.MONTE_CARLO, mapped.model());
    }

    @Test
    void leavesSpotAndVolatilityEmptySoTheServiceLooksThemUp() {
        PricingRequest pricing = QuoteRequestMapper.map(request(callOption())).pricingRequest();

        assertNull(pricing.spotPrice());
        assertNull(pricing.volatility());
        assertNull(pricing.riskFreeRate());
        assertNull(pricing.dividendYield());
        assertNull(pricing.marketPrice());
    }

    @Test
    void mapsPutOrCallZeroToPut() {
        QuoteRequest.NoRelatedSym instrument = callOption();
        instrument.set(new PutOrCall(PutOrCall.PUT));

        assertEquals(OptionType.PUT, QuoteRequestMapper.map(request(instrument)).pricingRequest().optionType());
    }

    @Test
    void defaultsToBlackScholesWhenNoModelIsGiven() {
        assertEquals(PricingModel.BLACK_SCHOLES, QuoteRequestMapper.map(request(callOption())).model());
    }

    @Test
    void acceptsEveryModelCodeInEitherCase() {
        assertEquals(PricingModel.BLACK_SCHOLES, PricingModel.fromFixCode("BS"));
        assertEquals(PricingModel.MONTE_CARLO, PricingModel.fromFixCode("mc"));
        assertEquals(PricingModel.BINOMIAL_TREE, PricingModel.fromFixCode("BT"));
    }

    @Test
    void rejectsAnUnknownModel() {
        QuoteRequest message = request(callOption());
        message.setString(PricingModel.FIX_TAG, "HESTON");

        assertRejected(message, "Unknown pricing model");
    }

    @Test
    void rejectsASecurityTypeOtherThanOption() {
        QuoteRequest.NoRelatedSym instrument = callOption();
        instrument.set(new SecurityType(SecurityType.COMMON_STOCK));

        assertRejected(request(instrument), "Only SecurityType (167) = OPT is supported");
    }

    @Test
    void rejectsAMissingSecurityType() {
        QuoteRequest.NoRelatedSym instrument = callOption();
        instrument.removeField(SecurityType.FIELD);

        assertRejected(request(instrument), "SecurityType (167) is required");
    }

    @Test
    void rejectsAMissingStrike() {
        QuoteRequest.NoRelatedSym instrument = callOption();
        instrument.removeField(StrikePrice.FIELD);

        assertRejected(request(instrument), "StrikePrice (202) is required");
    }

    @Test
    void rejectsAMissingPutOrCall() {
        QuoteRequest.NoRelatedSym instrument = callOption();
        instrument.removeField(PutOrCall.FIELD);

        assertRejected(request(instrument), "PutOrCall (201) is required");
    }

    @Test
    void rejectsAMissingMaturityDate() {
        QuoteRequest.NoRelatedSym instrument = callOption();
        instrument.removeField(MaturityDate.FIELD);

        assertRejected(request(instrument), "MaturityDate (541) is required");
    }

    @Test
    void rejectsAMaturityDateThatIsNotADate() {
        QuoteRequest.NoRelatedSym instrument = callOption();
        instrument.set(new MaturityDate("18-06-2099"));

        assertRejected(request(instrument), "YYYYMMDD");
    }

    @Test
    void rejectsMoreThanOneInstrument() {
        QuoteRequest message = request(callOption());
        message.addGroup(callOption());

        assertRejected(message, "Exactly one instrument");
    }

    private static void assertRejected(QuoteRequest message, String expectedText) {
        QuoteRejectException rejection = assertThrows(QuoteRejectException.class, () -> QuoteRequestMapper.map(message));
        assertEquals(QuoteRequestRejectReason.OTHER, rejection.rejectReason());
        assertTrue(rejection.getMessage().contains(expectedText), rejection.getMessage());
    }

    private static QuoteRequest request(QuoteRequest.NoRelatedSym instrument) {
        QuoteRequest message = new QuoteRequest(new QuoteReqID("REQ-1"));
        message.addGroup(instrument);
        return message;
    }

    private static QuoteRequest.NoRelatedSym callOption() {
        QuoteRequest.NoRelatedSym instrument = new QuoteRequest.NoRelatedSym();
        instrument.set(new Symbol("AAPL"));
        instrument.set(new SecurityType(SecurityType.OPTION));
        instrument.set(new MaturityDate("20990618"));
        instrument.set(new PutOrCall(PutOrCall.CALL));
        instrument.set(new StrikePrice(207.5));
        return instrument;
    }
}
