package com.optionpricing.service;

import com.optionpricing.dto.MarketDataResponse;
import com.optionpricing.dto.TickerSearchResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MarketDataServiceTest {
    private static final String AAPL_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/AAPL?range=3mo&interval=1d";

    private static final String SEARCH_URL =
            "https://query1.finance.yahoo.com/v1/finance/search?q=nvid&quotesCount=10&newsCount=0";
    private static final String TWO_CLOSES = """
            {"chart":{"result":[{
              "meta":{"regularMarketPrice":101.0},
              "indicators":{"quote":[{"close":[100.0,101.0]}]}
            }]}}
            """;

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final MarketDataService service = new MarketDataService(builder.build());

    @Test
    void returnsLivePriceAndAnnualisedHistoricalVolatility() {
        server.expect(requestTo(AAPL_URL)).andRespond(withSuccess("""
                {"chart":{"result":[{
                  "meta":{"regularMarketPrice":104.5},
                  "indicators":{"quote":[{"close":[100.0,102.0,null,101.0,104.0]}]}
                }]}}
                """, MediaType.APPLICATION_JSON));

        MarketDataResponse response = service.getMarketData(" aapl ");

        // Sample standard deviation of the three daily log returns, annualised over 252 trading days.
        double[] returns = {Math.log(102.0 / 100.0), Math.log(101.0 / 102.0), Math.log(104.0 / 101.0)};
        double mean = (returns[0] + returns[1] + returns[2]) / 3;
        double variance = (Math.pow(returns[0] - mean, 2) + Math.pow(returns[1] - mean, 2) + Math.pow(returns[2] - mean, 2)) / 2;

        assertThat(response.ticker()).isEqualTo("AAPL");
        assertThat(response.livePrice()).isEqualTo(104.5);
        assertThat(response.historicalVolatility()).isCloseTo(Math.sqrt(variance) * Math.sqrt(252), offset(1e-12));
    }

    @Test
    void fallsBackToLastCloseWhenLivePriceIsMissing() {
        server.expect(requestTo(AAPL_URL)).andRespond(withSuccess("""
                {"chart":{"result":[{
                  "meta":{},
                  "indicators":{"quote":[{"close":[100.0,102.0,101.0]}]}
                }]}}
                """, MediaType.APPLICATION_JSON));

        assertThat(service.getMarketData("AAPL").livePrice()).isEqualTo(101.0);
    }

    @Test
    void unknownTickerIsRejectedAsBadInput() {
        server.expect(requestTo(AAPL_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.getMarketData("AAPL"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void yahooOutageIsReportedAsMarketDataUnavailable() {
        server.expect(requestTo(AAPL_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> service.getMarketData("AAPL"))
                .isInstanceOf(MarketDataUnavailableException.class);
    }

    @Test
    void repeatLookupsWithinTheCacheWindowDoNotCallYahooAgain() {
        // MockRestServiceServer fails the test if a second, unexpected request is made.
        server.expect(requestTo(AAPL_URL)).andRespond(withSuccess(TWO_CLOSES, MediaType.APPLICATION_JSON));

        MarketDataResponse first = service.getMarketData("AAPL");
        MarketDataResponse second = service.getMarketData("aapl");

        assertThat(second).isEqualTo(first);
        server.verify();
    }

    @Test
    void requireKnownTickerRejectsSymbolsYahooDoesNotRecognise() {
        server.expect(requestTo(AAPL_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.requireKnownTicker("AAPL"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireKnownTickerAllowsTheRequestWhenYahooIsDown() {
        server.expect(requestTo(AAPL_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatCode(() -> service.requireKnownTicker("AAPL")).doesNotThrowAnyException();
    }

    @Test
    void searchReturnsOnlyStocksAndEtfsThatCanBePriced() {
        server.expect(requestTo(SEARCH_URL)).andRespond(withSuccess("""
                {"quotes":[
                  {"symbol":"NVDA","longname":"NVIDIA Corporation","shortname":"NVIDIA Corp","quoteType":"EQUITY","exchDisp":"NASDAQ"},
                  {"symbol":"SNVDA=F","shortname":"NVIDIA Corp Single Stock future","quoteType":"FUTURE","exchDisp":"CME"},
                  {"symbol":"NVDX","shortname":"T-Rex 2X Long NVIDIA","quoteType":"ETF","exchDisp":"BATS Trading"},
                  {"symbol":"^NVDIDX","shortname":"Some index","quoteType":"EQUITY","exchDisp":"X"}
                ]}
                """, MediaType.APPLICATION_JSON));

        assertThat(service.search(" nvid ")).containsExactly(
                new TickerSearchResult("NVDA", "NVIDIA Corporation", "NASDAQ"),
                new TickerSearchResult("NVDX", "T-Rex 2X Long NVIDIA", "BATS Trading"));
    }

    @Test
    void blankSearchReturnsNothingWithoutCallingYahoo() {
        assertThat(service.search("   ")).isEmpty();
        server.verify();
    }

    @Test
    void searchOutageIsReportedAsMarketDataUnavailable() {
        server.expect(requestTo(SEARCH_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> service.search("nvid"))
                .isInstanceOf(MarketDataUnavailableException.class);
    }
}
