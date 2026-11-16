package com.optionpricing.fix;

import com.optionpricing.dto.PricingRequest;
import com.optionpricing.dto.PricingResponse;
import com.optionpricing.fix.QuoteRequestMapper.MappedQuoteRequest;
import com.optionpricing.service.MarketDataUnavailableException;
import com.optionpricing.service.OptionPricingService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.Application;
import quickfix.FieldNotFound;
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.Message;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionNotFound;
import quickfix.UnsupportedMessageType;
import quickfix.field.BidPx;
import quickfix.field.BusinessRejectReason;
import quickfix.field.BusinessRejectRefID;
import quickfix.field.MidPx;
import quickfix.field.MsgType;
import quickfix.field.NoRelatedSym;
import quickfix.field.OfferPx;
import quickfix.field.QuoteID;
import quickfix.field.QuoteReqID;
import quickfix.field.QuoteRequestRejectReason;
import quickfix.field.RefMsgType;
import quickfix.field.Symbol;
import quickfix.field.Text;
import quickfix.field.TransactTime;
import quickfix.fix44.BusinessMessageReject;
import quickfix.fix44.MessageCracker;
import quickfix.fix44.Quote;
import quickfix.fix44.QuoteRequest;
import quickfix.fix44.QuoteRequestReject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

/**
 * The FIX side of the pricing engine. QuickFIX/J calls this class for every session event
 * and every message; the engine itself handles logon, heartbeats and sequence numbers.
 * <p>
 * We act on one message type: a QuoteRequest (35=R) is priced by the same
 * OptionPricingService the REST API uses and answered with a Quote (35=S), or with a
 * QuoteRequestReject (35=AG) when the request cannot be priced.
 * <p>
 * MessageCracker is QuickFIX/J's dispatcher: crack() looks at the message type and calls
 * the matching onMessage method. Types we have no method for raise UnsupportedMessageType,
 * which the engine turns into a BusinessMessageReject (35=j) for us.
 */
public class FixApplication extends MessageCracker implements Application {
    private static final Logger logger = LoggerFactory.getLogger(FixApplication.class);
    // Quoted prices are rounded to four decimal places so the FIX message stays readable.
    private static final int PRICE_DECIMALS = 4;
    private static final double BASIS_POINTS = 10_000.0;

    private final OptionPricingService pricingService;
    private final Validator validator;
    private final double spreadBps;

    public FixApplication(OptionPricingService pricingService, Validator validator, double spreadBps) {
        this.pricingService = pricingService;
        this.validator = validator;
        this.spreadBps = spreadBps;
    }

    // Application-level messages from the client arrive here.
    @Override
    public void fromApp(Message message, SessionID sessionId)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        crack(message, sessionId);
    }

    @Override
    public void onMessage(QuoteRequest message, SessionID sessionId) throws FieldNotFound {
        String quoteReqId = message.getString(QuoteReqID.FIELD);
        Message reply;
        try {
            reply = price(message, quoteReqId);
        } catch (QuoteRejectException ex) {
            reply = reject(message, quoteReqId, ex.rejectReason(), ex.getMessage());
        } catch (IllegalArgumentException ex) {
            // How the pricing service reports bad input, such as an unknown ticker or a past expiry.
            reply = reject(message, quoteReqId, QuoteRequestRejectReason.OTHER, ex.getMessage());
        } catch (MarketDataUnavailableException ex) {
            reply = reject(message, quoteReqId, QuoteRequestRejectReason.OTHER, "Market data is unavailable; try again later.");
        } catch (RuntimeException ex) {
            // Anything unexpected (for example the database being down). The client still gets
            // an answer and the session stays up.
            logger.error("Could not process QuoteRequest {}", quoteReqId, ex);
            reply = applicationUnavailable(quoteReqId);
        }
        send(reply, sessionId);
    }

    private Quote price(QuoteRequest message, String quoteReqId) throws FieldNotFound {
        MappedQuoteRequest mapped = QuoteRequestMapper.map(message);
        PricingRequest pricingRequest = mapped.pricingRequest();
        validate(pricingRequest);

        // The same call the REST controller makes: it prices with all three models and saves
        // the request to the pricing_requests table.
        PricingResponse response = pricingService.price(pricingRequest);
        double theoretical = mapped.model().priceFrom(response);

        // Half the spread goes either side of the theoretical price. A bid cannot be negative.
        double halfSpread = theoretical * spreadBps / BASIS_POINTS / 2;
        double bid = Math.max(0, theoretical - halfSpread);
        double offer = theoretical + halfSpread;

        Quote quote = new Quote(new QuoteID(UUID.randomUUID().toString()));
        // Echoing the client's request id is how it matches this quote to its request.
        quote.set(new QuoteReqID(quoteReqId));
        copyInstrument(message, quote);
        quote.set(new BidPx(round(bid)));
        quote.set(new OfferPx(round(offer)));
        quote.set(new MidPx(round(theoretical)));
        quote.setString(PricingModel.FIX_TAG, mapped.model().fixCode());
        quote.set(new TransactTime(LocalDateTime.now(ZoneOffset.UTC)));
        return quote;
    }

    // Applies the annotations on PricingRequest (@NotNull, @Future, the ticker pattern...).
    // The REST controller gets this from @Valid; here we ask the same validator directly.
    private void validate(PricingRequest pricingRequest) {
        Set<ConstraintViolation<PricingRequest>> violations = validator.validate(pricingRequest);
        if (!violations.isEmpty()) {
            ConstraintViolation<PricingRequest> first = violations.iterator().next();
            throw new QuoteRejectException(QuoteRequestRejectReason.OTHER,
                    first.getPropertyPath() + " " + first.getMessage());
        }
    }

    // The quote describes the same option that was asked about, so the instrument fields
    // (symbol, security type, maturity, put/call, strike) are copied across unchanged.
    private void copyInstrument(QuoteRequest request, Quote quote) throws FieldNotFound {
        QuoteRequest.NoRelatedSym instrument = new QuoteRequest.NoRelatedSym();
        request.getGroup(1, instrument);
        quote.set(instrument.getInstrument());
    }

    private QuoteRequestReject reject(QuoteRequest request, String quoteReqId, int reason, String text) {
        logger.info("Rejected QuoteRequest {}: {}", quoteReqId, text);
        QuoteRequestReject reject = new QuoteRequestReject(
                new QuoteReqID(quoteReqId), new QuoteRequestRejectReason(reason));
        // FIX requires the reject to name at least one instrument. "[N/A]" is the FIX
        // convention for "no symbol"; it is only a fallback, since the engine has already
        // rejected any request that arrives without one.
        QuoteRequestReject.NoRelatedSym instrument = new QuoteRequestReject.NoRelatedSym();
        instrument.set(new Symbol(firstSymbol(request)));
        reject.addGroup(instrument);
        reject.set(new Text(text));
        return reject;
    }

    private String firstSymbol(QuoteRequest request) {
        try {
            return request.getGroup(1, NoRelatedSym.FIELD).getString(Symbol.FIELD);
        } catch (FieldNotFound ex) {
            return "[N/A]";
        }
    }

    private BusinessMessageReject applicationUnavailable(String quoteReqId) {
        BusinessMessageReject reject = new BusinessMessageReject(
                new RefMsgType(MsgType.QUOTE_REQUEST),
                new BusinessRejectReason(BusinessRejectReason.APPLICATION_NOT_AVAILABLE));
        reject.set(new BusinessRejectRefID(quoteReqId));
        reject.set(new Text("The pricing engine could not process the request."));
        return reject;
    }

    private void send(Message reply, SessionID sessionId) {
        try {
            Session.sendToTarget(reply, sessionId);
        } catch (SessionNotFound ex) {
            // The client disconnected between asking and being answered.
            logger.warn("Could not send reply; FIX session {} no longer exists", sessionId);
        }
    }

    private static double round(double price) {
        return BigDecimal.valueOf(price).setScale(PRICE_DECIMALS, RoundingMode.HALF_UP).doubleValue();
    }

    @Override
    public void onLogon(SessionID sessionId) {
        logger.info("FIX client logged on: {}", sessionId);
    }

    @Override
    public void onLogout(SessionID sessionId) {
        logger.info("FIX client logged out: {}", sessionId);
    }

    // The remaining callbacks are hooks we do not need: session created, and session-level
    // (admin) or outgoing messages passing through.
    @Override
    public void onCreate(SessionID sessionId) {
    }

    @Override
    public void toAdmin(Message message, SessionID sessionId) {
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionId) {
    }

    @Override
    public void toApp(Message message, SessionID sessionId) {
    }
}
