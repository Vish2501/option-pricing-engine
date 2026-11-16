package com.optionpricing.fix;

import com.optionpricing.dto.MarketDataResponse;
import com.optionpricing.model.OptionInputs;
import com.optionpricing.model.OptionType;
import com.optionpricing.repository.PricingRequestLogRepository;
import com.optionpricing.service.BlackScholesService;
import com.optionpricing.service.MarketDataService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import quickfix.Application;
import quickfix.DefaultMessageFactory;
import quickfix.Initiator;
import quickfix.MemoryStoreFactory;
import quickfix.Message;
import quickfix.SLF4JLogFactory;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionSettings;
import quickfix.SocketInitiator;
import quickfix.field.BidPx;
import quickfix.field.BusinessRejectReason;
import quickfix.field.MaturityDate;
import quickfix.field.MidPx;
import quickfix.field.MsgType;
import quickfix.field.OfferPx;
import quickfix.field.PutOrCall;
import quickfix.field.QuoteReqID;
import quickfix.field.QuoteRequestRejectReason;
import quickfix.field.RefMsgType;
import quickfix.field.SecurityType;
import quickfix.field.StrikePrice;
import quickfix.field.Symbol;
import quickfix.field.Text;
import quickfix.fix44.QuoteRequest;
import quickfix.fix44.QuoteStatusRequest;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Starts the whole app with the FIX acceptor switched on, connects a real QuickFIX/J client
 * (an "initiator") to it over a local TCP socket, and checks the replies. Market data is
 * mocked and the database is in-memory H2, so nothing outside this JVM is needed.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:fixtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "fix.enabled=true",
        "fix.spread-bps=100"
})
class FixAcceptorIntegrationTest {
    private static final int PORT = freePort();
    private static final double SPOT = 226.5;
    private static final double VOLATILITY = 0.2191;
    private static final double STRIKE = 207.5;
    private static final long TIMEOUT_SECONDS = 10;

    @DynamicPropertySource
    static void fixPort(DynamicPropertyRegistry registry) {
        registry.add("fix.port", () -> PORT);
    }

    @MockBean
    private MarketDataService marketDataService;

    @Autowired
    private BlackScholesService blackScholesService;

    @Autowired
    private PricingRequestLogRepository repository;

    private final LocalDate expiry = LocalDate.now().plusDays(30);
    private TestClient client;
    private Initiator initiator;
    private SessionID sessionId;

    @BeforeEach
    void connectClient() throws Exception {
        when(marketDataService.getMarketData("AAPL")).thenReturn(new MarketDataResponse("AAPL", SPOT, VOLATILITY));
        repository.deleteAll();

        // The mirror image of acceptor.cfg: we are CLIENT, talking to PRICER.
        sessionId = new SessionID("FIX.4.4", "CLIENT", "PRICER");
        SessionSettings settings = new SessionSettings();
        settings.setString("ConnectionType", "initiator");
        settings.setString("NonStopSession", "Y");
        settings.setString("ResetOnLogon", "Y");
        settings.setString("ValidateUserDefinedFields", "N");
        settings.setString(sessionId, "BeginString", "FIX.4.4");
        settings.setString(sessionId, "SocketConnectHost", "localhost");
        settings.setLong(sessionId, "SocketConnectPort", PORT);
        settings.setLong(sessionId, "HeartBtInt", 30);

        client = new TestClient();
        initiator = new SocketInitiator(client, new MemoryStoreFactory(), settings,
                new SLF4JLogFactory(settings), new DefaultMessageFactory());
        initiator.start();
        assertTrue(client.loggedOn.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "client did not log on");
    }

    @AfterEach
    void disconnectClient() {
        initiator.stop();
    }

    @Test
    void validQuoteRequestIsAnsweredWithAQuoteAndLogged() throws Exception {
        Session.sendToTarget(quoteRequest("REQ-1", STRIKE), sessionId);

        Message quote = client.nextMessage();
        assertEquals(MsgType.QUOTE, quote.getHeader().getString(MsgType.FIELD));
        assertEquals("REQ-1", quote.getString(QuoteReqID.FIELD));
        assertEquals("AAPL", quote.getString(Symbol.FIELD));
        assertEquals(STRIKE, quote.getDouble(StrikePrice.FIELD));
        assertEquals(PutOrCall.CALL, quote.getInt(PutOrCall.FIELD));
        assertEquals("BS", quote.getString(PricingModel.FIX_TAG));

        // No model was requested, so the mid is the Black-Scholes price for the mocked market data.
        double timeYears = ChronoUnit.DAYS.between(LocalDate.now(), expiry) / 365.0;
        double expected = blackScholesService.price(OptionType.CALL,
                new OptionInputs(SPOT, STRIKE, timeYears, 0.05, 0.0, VOLATILITY));
        double mid = quote.getDouble(MidPx.FIELD);
        assertEquals(expected, mid, 1e-4);
        // A 100 bp spread is 0.5% either side of the mid.
        assertEquals(mid * 0.995, quote.getDouble(BidPx.FIELD), 1e-4);
        assertEquals(mid * 1.005, quote.getDouble(OfferPx.FIELD), 1e-4);

        // Saved through the same path as a REST request.
        assertEquals(1, repository.count());
        assertEquals("AAPL", repository.findAll().get(0).getTicker());
    }

    @Test
    void requestedModelDecidesWhichPriceIsQuoted() throws Exception {
        QuoteRequest request = quoteRequest("REQ-2", STRIKE);
        request.setString(PricingModel.FIX_TAG, "BT");
        Session.sendToTarget(request, sessionId);

        Message quote = client.nextMessage();
        assertEquals(MsgType.QUOTE, quote.getHeader().getString(MsgType.FIELD));
        assertEquals("BT", quote.getString(PricingModel.FIX_TAG));
        assertEquals(repository.findAll().get(0).getBinomialTreePrice(), quote.getDouble(MidPx.FIELD), 1e-4);
    }

    @Test
    void invalidQuoteRequestIsAnsweredWithAQuoteRequestReject() throws Exception {
        Session.sendToTarget(quoteRequest("REQ-3", -5.0), sessionId);

        Message reject = client.nextMessage();
        assertEquals(MsgType.QUOTE_REQUEST_REJECT, reject.getHeader().getString(MsgType.FIELD));
        assertEquals("REQ-3", reject.getString(QuoteReqID.FIELD));
        assertEquals(QuoteRequestRejectReason.OTHER, reject.getInt(QuoteRequestRejectReason.FIELD));
        assertTrue(reject.getString(Text.FIELD).contains("strike"), reject.getString(Text.FIELD));
        assertEquals(0, repository.count());

        // The session survived the bad request: a good one is still quoted.
        Session.sendToTarget(quoteRequest("REQ-4", STRIKE), sessionId);
        assertEquals(MsgType.QUOTE, client.nextMessage().getHeader().getString(MsgType.FIELD));
    }

    @Test
    void unsupportedMessageTypeIsAnsweredWithABusinessMessageReject() throws Exception {
        QuoteStatusRequest unsupported = new QuoteStatusRequest();
        unsupported.set(new Symbol("AAPL"));
        Session.sendToTarget(unsupported, sessionId);

        Message reject = client.nextMessage();
        assertEquals(MsgType.BUSINESS_MESSAGE_REJECT, reject.getHeader().getString(MsgType.FIELD));
        assertEquals(MsgType.QUOTE_STATUS_REQUEST, reject.getString(RefMsgType.FIELD));
        assertEquals(BusinessRejectReason.UNSUPPORTED_MESSAGE_TYPE, reject.getInt(BusinessRejectReason.FIELD));
    }

    private QuoteRequest quoteRequest(String quoteReqId, double strike) {
        QuoteRequest.NoRelatedSym instrument = new QuoteRequest.NoRelatedSym();
        instrument.set(new Symbol("AAPL"));
        instrument.set(new SecurityType(SecurityType.OPTION));
        instrument.set(new MaturityDate(expiry.format(DateTimeFormatter.BASIC_ISO_DATE)));
        instrument.set(new PutOrCall(PutOrCall.CALL));
        instrument.set(new StrikePrice(strike));

        QuoteRequest request = new QuoteRequest(new QuoteReqID(quoteReqId));
        request.addGroup(instrument);
        return request;
    }

    // Asks the operating system for a port nobody is using, so the test cannot clash with
    // another process (or a locally running copy of the app on 9876).
    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException("No free port available", ex);
        }
    }

    /** A minimal FIX client: notes when it has logged on and queues every reply it receives. */
    private static class TestClient implements Application {
        private final CountDownLatch loggedOn = new CountDownLatch(1);
        private final BlockingQueue<Message> received = new LinkedBlockingQueue<>();

        Message nextMessage() throws InterruptedException {
            Message message = received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertNotNull(message, "no FIX reply within " + TIMEOUT_SECONDS + " seconds");
            return message;
        }

        @Override
        public void onLogon(SessionID sessionId) {
            loggedOn.countDown();
        }

        @Override
        public void fromApp(Message message, SessionID sessionId) {
            received.add(message);
        }

        @Override
        public void onCreate(SessionID sessionId) {
        }

        @Override
        public void onLogout(SessionID sessionId) {
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
}
