import React, { useEffect, useRef, useState } from "react";
import { Activity, Calculator, Loader2, Search } from "lucide-react";
import { fetchComparison, fetchMarketData, priceOption, searchTickers } from "../api";
import { formatNumber, formatPercent } from "../format";
import type { Comparison, MarketData, OptionType, PricingResponse, TickerSuggestion } from "../types";
import type { TrackRequest } from "../useWakeNotice";

const SEARCH_DEBOUNCE_MS = 300;

const defaultExpiry = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);

// An empty box means "let the server decide" (live price, historical volatility, default rate).
function numberOrNull(text: string) {
  return text.trim() ? Number(text) : null;
}

type Props = {
  track: TrackRequest;
  onPriced: (result: PricingResponse, marketPriceEntered: boolean) => void;
  onError: (message: string) => void;
};

export function PricingForm({ track, onPriced, onError }: Props) {
  const [ticker, setTicker] = useState("AAPL");
  const [spotPrice, setSpotPrice] = useState("226.5");
  const [strike, setStrike] = useState("207.5");
  const [expiry, setExpiry] = useState(defaultExpiry);
  const [optionType, setOptionType] = useState<OptionType>("CALL");
  const [volatility, setVolatility] = useState("0.2191");
  const [riskFreeRate, setRiskFreeRate] = useState("0.05");
  const [dividendYield, setDividendYield] = useState("0");
  const [marketPrice, setMarketPrice] = useState("22.25");
  const [marketData, setMarketData] = useState<MarketData | null>(null);
  const [comparison, setComparison] = useState<Comparison | null>(null);
  const [suggestions, setSuggestions] = useState<TickerSuggestion[]>([]);
  // The symbol last chosen from the suggestions, so choosing one does not trigger another search.
  const chosenTicker = useRef(ticker);
  const [pricing, setPricing] = useState(false);
  const [lookingUp, setLookingUp] = useState(false);

  // Search by company name or symbol as the user types, after a short pause.
  useEffect(() => {
    const query = ticker.trim();
    if (query.length < 2 || query === chosenTicker.current) {
      setSuggestions([]);
      return;
    }

    let stale = false;
    const timer = window.setTimeout(async () => {
      try {
        const results = await searchTickers(query);
        if (!stale) setSuggestions(results);
      } catch {
        if (!stale) setSuggestions([]);
      }
    }, SEARCH_DEBOUNCE_MS);

    return () => {
      stale = true;
      window.clearTimeout(timer);
    };
  }, [ticker]);

  function chooseSuggestion(suggestion: TickerSuggestion) {
    chosenTicker.current = suggestion.symbol;
    setTicker(suggestion.symbol);
    setSuggestions([]);
    void lookupStock(suggestion.symbol);
  }

  async function lookupStock(symbol = ticker) {
    setLookingUp(true);
    setComparison(null);
    onError("");
    try {
      const data = await track(fetchMarketData(symbol));
      setMarketData(data);
      setSpotPrice(String(Number(data.livePrice.toFixed(4))));
      setVolatility(String(Number(data.historicalVolatility.toFixed(4))));
      // A quick reference point for the stock; the form still works if this fails.
      setComparison(await fetchComparison(symbol).catch(() => null));
    } catch (err) {
      onError(err instanceof Error ? err.message : "Unable to fetch stock data.");
    } finally {
      setLookingUp(false);
    }
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setPricing(true);
    onError("");
    try {
      const result = await track(priceOption({
        ticker,
        spotPrice: numberOrNull(spotPrice),
        strike: Number(strike),
        expiry,
        optionType,
        volatility: numberOrNull(volatility),
        riskFreeRate: numberOrNull(riskFreeRate),
        dividendYield: numberOrNull(dividendYield),
        marketPrice: numberOrNull(marketPrice)
      }));
      onPriced(result, marketPrice.trim() !== "");
    } catch (err) {
      onError(err instanceof Error ? err.message : "Unable to price option.");
    } finally {
      setPricing(false);
    }
  }

  return (
    <form className="panel pricing-form" onSubmit={submit}>
      <div className="panel-heading">
        <Calculator size={20} />
        <h2>Price Option</h2>
      </div>

      <div className="field-grid">
        <label className="ticker-field">
          <span>Ticker</span>
          <div className="inline-input">
            <input
              value={ticker}
              placeholder="Symbol or company"
              autoComplete="off"
              onChange={(event) => setTicker(event.target.value.toUpperCase())}
              onKeyDown={(event) => event.key === "Escape" && setSuggestions([])}
              onBlur={() => setSuggestions([])}
            />
            <button type="button" className="icon-button" onClick={() => lookupStock()} title="Fetch market data">
              {lookingUp ? <Loader2 className="spin" size={17} /> : <Search size={17} />}
            </button>
          </div>
          {suggestions.length > 0 && (
            <ul className="suggestions" role="listbox">
              {suggestions.map((suggestion) => (
                <li
                  key={suggestion.symbol}
                  role="option"
                  aria-selected={false}
                  // mousedown fires before the input's blur, which would otherwise close the list first.
                  onMouseDown={(event) => {
                    event.preventDefault();
                    chooseSuggestion(suggestion);
                  }}
                >
                  <strong>{suggestion.symbol}</strong>
                  <span>{suggestion.name}</span>
                  <small>{suggestion.exchange}</small>
                </li>
              ))}
            </ul>
          )}
        </label>
        <label>
          <span>Option Type</span>
          <div className="segmented">
            <button type="button" className={optionType === "CALL" ? "active" : ""} onClick={() => setOptionType("CALL")}>
              Call
            </button>
            <button type="button" className={optionType === "PUT" ? "active" : ""} onClick={() => setOptionType("PUT")}>
              Put
            </button>
          </div>
        </label>
        <label>
          <span>Spot Price</span>
          <input inputMode="decimal" value={spotPrice} onChange={(event) => setSpotPrice(event.target.value)} />
        </label>
        <label>
          <span>Strike</span>
          <input inputMode="decimal" value={strike} onChange={(event) => setStrike(event.target.value)} />
        </label>
        <label>
          <span>Expiry</span>
          <input type="date" value={expiry} onChange={(event) => setExpiry(event.target.value)} />
        </label>
        <label>
          <span>Volatility</span>
          <input inputMode="decimal" value={volatility} onChange={(event) => setVolatility(event.target.value)} />
        </label>
        <label>
          <span>Risk-Free Rate</span>
          <input inputMode="decimal" value={riskFreeRate} onChange={(event) => setRiskFreeRate(event.target.value)} />
        </label>
        <label title="Annual dividend yield as a decimal, for example 0.02 for 2%">
          <span>Dividend Yield</span>
          <input inputMode="decimal" value={dividendYield} onChange={(event) => setDividendYield(event.target.value)} />
        </label>
        <label className="wide-field" title="What the option trades for; used to back out implied volatility">
          <span>Market Price (optional)</span>
          <input inputMode="decimal" value={marketPrice} onChange={(event) => setMarketPrice(event.target.value)} />
        </label>
      </div>

      {marketData && (
        <div className="market-strip">
          <div>
            <span>{marketData.ticker}</span>
            <strong>${formatNumber(marketData.livePrice)}</strong>
            <span>Vol {formatPercent(marketData.historicalVolatility)}</span>
          </div>
          {comparison && (
            <div title="Black-Scholes prices for options struck at the current price, expiring in 30 days">
              <span>30-day at-the-money:</span>
              <span>call <strong>${formatNumber(comparison.call.blackScholesPrice, 2)}</strong></span>
              <span>put <strong>${formatNumber(comparison.put.blackScholesPrice, 2)}</strong></span>
            </div>
          )}
        </div>
      )}

      <button className="primary-action" type="submit" disabled={pricing}>
        {pricing ? <Loader2 className="spin" size={18} /> : <Activity size={18} />}
        Calculate
      </button>
    </form>
  );
}
