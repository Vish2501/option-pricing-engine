package com.optionpricing.fix;

import com.optionpricing.dto.PricingRequest;
import com.optionpricing.model.OptionType;
import quickfix.FieldException;
import quickfix.FieldNotFound;
import quickfix.field.MaturityDate;
import quickfix.field.NoRelatedSym;
import quickfix.field.PutOrCall;
import quickfix.field.QuoteRequestRejectReason;
import quickfix.field.SecurityType;
import quickfix.field.StrikePrice;
import quickfix.field.Symbol;
import quickfix.fix44.QuoteRequest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Translates a FIX QuoteRequest (35=R) into the same PricingRequest the REST API uses.
 * It only reads and converts fields; whether the values make sense (positive strike,
 * future expiry, real ticker) is decided later by the same rules the REST path applies.
 */
public final class QuoteRequestMapper {
    // FIX dates (type LocalMktDate) are written YYYYMMDD, for example 20270618.
    private static final DateTimeFormatter FIX_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private QuoteRequestMapper() {
    }

    /** The pricing request plus the model the client wants quoted. */
    public record MappedQuoteRequest(PricingRequest pricingRequest, PricingModel model) {
    }

    public static MappedQuoteRequest map(QuoteRequest message) {
        try {
            // FIX lets one QuoteRequest ask about several instruments, as a repeating group.
            // We quote one option per request to keep the request/reply pairing simple.
            if (message.getInt(NoRelatedSym.FIELD) != 1) {
                throw reject("Exactly one instrument per QuoteRequest is supported.");
            }
            QuoteRequest.NoRelatedSym instrument = new QuoteRequest.NoRelatedSym();
            message.getGroup(1, instrument);

            requireOption(instrument);
            PricingRequest pricingRequest = new PricingRequest(
                    instrument.getString(Symbol.FIELD),
                    // Spot and volatility are left out, so the pricing service fetches them from
                    // market data, exactly as it does for a REST request that omits them.
                    null,
                    required(instrument, StrikePrice.FIELD, "StrikePrice (202)").getDouble(StrikePrice.FIELD),
                    expiry(instrument),
                    optionType(instrument),
                    null,
                    null,
                    null,
                    null
            );
            return new MappedQuoteRequest(pricingRequest, model(message));
        } catch (FieldNotFound ex) {
            throw reject("Required field " + ex.field + " is missing.");
        } catch (FieldException ex) {
            // For example a strike that is not a number.
            throw reject("Field has an invalid value: " + ex.getMessage());
        }
    }

    private static void requireOption(QuoteRequest.NoRelatedSym instrument) throws FieldNotFound {
        String securityType = required(instrument, SecurityType.FIELD, "SecurityType (167)").getString(SecurityType.FIELD);
        if (!SecurityType.OPTION.equals(securityType)) {
            throw reject("Only SecurityType (167) = OPT is supported, got " + securityType + ".");
        }
    }

    private static OptionType optionType(QuoteRequest.NoRelatedSym instrument) throws FieldNotFound {
        int putOrCall = required(instrument, PutOrCall.FIELD, "PutOrCall (201)").getInt(PutOrCall.FIELD);
        return switch (putOrCall) {
            case PutOrCall.PUT -> OptionType.PUT;
            case PutOrCall.CALL -> OptionType.CALL;
            default -> throw reject("PutOrCall (201) must be 0 (put) or 1 (call).");
        };
    }

    private static LocalDate expiry(QuoteRequest.NoRelatedSym instrument) throws FieldNotFound {
        String maturityDate = required(instrument, MaturityDate.FIELD, "MaturityDate (541)").getString(MaturityDate.FIELD);
        try {
            return LocalDate.parse(maturityDate, FIX_DATE);
        } catch (DateTimeParseException ex) {
            throw reject("MaturityDate (541) must be a date in YYYYMMDD format.");
        }
    }

    // The model tag sits in the message body, after the instrument group. It is optional.
    private static PricingModel model(QuoteRequest message) throws FieldNotFound {
        if (!message.isSetField(PricingModel.FIX_TAG)) {
            return PricingModel.BLACK_SCHOLES;
        }
        try {
            return PricingModel.fromFixCode(message.getString(PricingModel.FIX_TAG));
        } catch (IllegalArgumentException ex) {
            throw reject(ex.getMessage());
        }
    }

    // These fields are optional in the FIX 4.4 dictionary but we need them to price an option,
    // so their absence gets a clear message rather than a bare tag number.
    private static QuoteRequest.NoRelatedSym required(QuoteRequest.NoRelatedSym instrument, int tag, String name) {
        if (!instrument.isSetField(tag)) {
            throw reject(name + " is required.");
        }
        return instrument;
    }

    private static QuoteRejectException reject(String message) {
        return new QuoteRejectException(QuoteRequestRejectReason.OTHER, message);
    }
}
