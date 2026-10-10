import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  Activity,
  BarChart3,
  Calculator,
  Database,
  Loader2,
  PieChart,
  RefreshCw,
  Search
} from "lucide-react";
import "./styles.css";

type OptionType = "CALL" | "PUT";

type Greeks = {
  delta: number;
  gamma: number;
  vega: number;
  theta: number;
  rho: number;
};

type PricingResponse = {
  ticker: string;
  spotPrice: number;
  strike: number;
  expiry: string;
  timeToMaturityYears: number;
  optionType: OptionType;
  riskFreeRate: number;
  volatility: number;
  blackScholesPrice: number;
  monteCarloPrice: number;
  binomialTreePrice: number;
  impliedVolatility: number | null;
  greeks: Greeks;
};

type MarketData = {
  ticker: string;
  livePrice: number;
  historicalVolatility: number;
};

type HistoryRow = PricingResponse & {
  id: number;
  requestedAt: string;
  delta: number;
  gamma: number;
  vega: number;
  theta: number;
  rho: number;
};

type Analytics = {
  totalRequests: number;
  uniqueTickers: number;
  requestsByTicker: Record<string, number>;
  averageBlackScholesPrice: number;
  averageMonteCarloPrice: number;
  averageBinomialTreePrice: number;
  averageModelSpread: number;
};

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "/api/v1";
const API_KEY = import.meta.env.VITE_API_KEY as string | undefined;

const WAKE_NOTICE_DELAY_MS = 4000;
const MAX_TICKER_BARS = 8;

const today = new Date();
const defaultExpiry = new Date(today.getTime() + 30 * 24 * 60 * 60 * 1000)
  .toISOString()
  .slice(0, 10);

function formatNumber(value: number | null | undefined, digits = 4) {
  if (value === null || value === undefined || Number.isNaN(value)) return "—";
  return new Intl.NumberFormat("en-US", {
    maximumFractionDigits: digits,
    minimumFractionDigits: Math.min(2, digits)
  }).format(value);
}

async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers);
  if (API_KEY) {
    headers.set("X-API-Key", API_KEY);
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${url}`, { ...init, headers });
  } catch {
    throw new Error("The server is still waking up. Please try again in a minute.");
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.message ?? `Request failed with ${response.status}`);
  }
  return response.json();
}

function App() {
  const [ticker, setTicker] = useState("AAPL");
  const [spotPrice, setSpotPrice] = useState("226.5");
  const [strike, setStrike] = useState("207.5");
  const [expiry, setExpiry] = useState(defaultExpiry);
  const [optionType, setOptionType] = useState<OptionType>("CALL");
  const [volatility, setVolatility] = useState("0.2191");
  const [riskFreeRate, setRiskFreeRate] = useState("0.05");
  const [marketPrice, setMarketPrice] = useState("22.25");
  const [result, setResult] = useState<PricingResponse | null>(null);
  const [marketData, setMarketData] = useState<MarketData | null>(null);
  const [history, setHistory] = useState<HistoryRow[]>([]);
  const [analytics, setAnalytics] = useState<Analytics | null>(null);
  const [loading, setLoading] = useState(false);
  const [stockLoading, setStockLoading] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [error, setError] = useState("");
  const [waking, setWaking] = useState(false);

  const modelRows = useMemo(() => {
    if (!result) return [];
    return [
      ["Black-Scholes", result.blackScholesPrice],
      ["Monte Carlo", result.monteCarloPrice],
      ["Binomial Tree", result.binomialTreePrice]
    ];
  }, [result]);

  // The API already returns tickers busiest-first; show the top few as bars.
  const tickerCounts = useMemo(
    () => Object.entries(analytics?.requestsByTicker ?? {}).slice(0, MAX_TICKER_BARS),
    [analytics]
  );
  const busiestCount = tickerCounts.length ? tickerCounts[0][1] : 0;

  useEffect(() => {
    void loadHistory();
  }, []);

  // The API runs on free hosting that sleeps when idle, so a slow request usually
  // means a cold start - tell the user instead of leaving them with a spinner.
  async function withWakeNotice<T>(request: Promise<T>): Promise<T> {
    const timer = window.setTimeout(() => setWaking(true), WAKE_NOTICE_DELAY_MS);
    try {
      return await request;
    } finally {
      window.clearTimeout(timer);
      setWaking(false);
    }
  }

  async function priceOption(event: React.FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError("");
    try {
      const payload = {
        ticker,
        spotPrice: spotPrice ? Number(spotPrice) : null,
        strike: Number(strike),
        expiry,
        optionType,
        volatility: volatility ? Number(volatility) : null,
        riskFreeRate: riskFreeRate ? Number(riskFreeRate) : null,
        marketPrice: marketPrice ? Number(marketPrice) : null
      };
      const data = await withWakeNotice(requestJson<PricingResponse>("/price", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      }));
      setResult(data);
      await loadHistory();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to price option.");
    } finally {
      setLoading(false);
    }
  }

  async function lookupStock() {
    setStockLoading(true);
    setError("");
    try {
      const data = await withWakeNotice(requestJson<MarketData>(`/stock/${ticker}`));
      setMarketData(data);
      setSpotPrice(String(Number(data.livePrice.toFixed(4))));
      setVolatility(String(Number(data.historicalVolatility.toFixed(4))));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to fetch stock data.");
    } finally {
      setStockLoading(false);
    }
  }

  async function loadHistory() {
    setHistoryLoading(true);
    try {
      const [rows, summary] = await withWakeNotice(Promise.all([
        requestJson<HistoryRow[]>("/history"),
        requestJson<Analytics>("/history/analytics")
      ]));
      setHistory(rows.slice(0, 8));
      setAnalytics(summary);
    } catch {
      setHistory([]);
      setAnalytics(null);
    } finally {
      setHistoryLoading(false);
    }
  }

  return (
    <main className="app-shell">
      <section className="topbar">
        <div>
          <h1>Options Pricing Engine</h1>
          <p>Black-Scholes, Monte Carlo, Binomial Tree, Greeks, implied volatility, and request history.</p>
        </div>
        <button className="icon-button" type="button" onClick={loadHistory} title="Refresh history">
          {historyLoading ? <Loader2 className="spin" size={18} /> : <RefreshCw size={18} />}
        </button>
      </section>

      {error && <div className="error-banner">{error}</div>}
      {waking && (
        <div className="waking-banner">
          <Loader2 className="spin" size={16} />
          Waking the server. It runs on free hosting that sleeps when idle, so the first request can take up to three minutes.
        </div>
      )}

      <section className="workspace">
        <form className="panel pricing-form" onSubmit={priceOption}>
          <div className="panel-heading">
            <Calculator size={20} />
            <h2>Price Option</h2>
          </div>

          <div className="field-grid">
            <label>
              <span>Ticker</span>
              <div className="inline-input">
                <input value={ticker} onChange={(event) => setTicker(event.target.value.toUpperCase())} />
                <button type="button" className="icon-button" onClick={lookupStock} title="Fetch market data">
                  {stockLoading ? <Loader2 className="spin" size={17} /> : <Search size={17} />}
                </button>
              </div>
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
            <label>
              <span>Market Price</span>
              <input inputMode="decimal" value={marketPrice} onChange={(event) => setMarketPrice(event.target.value)} />
            </label>
          </div>

          {marketData && (
            <div className="market-strip">
              <span>{marketData.ticker}</span>
              <strong>${formatNumber(marketData.livePrice)}</strong>
              <span>Vol {formatNumber(marketData.historicalVolatility * 100, 2)}%</span>
            </div>
          )}

          <button className="primary-action" type="submit" disabled={loading}>
            {loading ? <Loader2 className="spin" size={18} /> : <Activity size={18} />}
            Calculate
          </button>
        </form>

        <section className="panel results-panel">
          <div className="panel-heading">
            <BarChart3 size={20} />
            <h2>Model Outputs</h2>
          </div>

          {result ? (
            <>
              <div className="metric-grid">
                {modelRows.map(([label, value]) => (
                  <div className="metric" key={label as string}>
                    <span>{label}</span>
                    <strong>${formatNumber(value as number)}</strong>
                  </div>
                ))}
                <div className="metric">
                  <span>Implied Volatility</span>
                  <strong>{result.impliedVolatility ? `${formatNumber(result.impliedVolatility * 100, 2)}%` : "—"}</strong>
                </div>
              </div>

              <div className="greeks-grid">
                {Object.entries(result.greeks).map(([key, value]) => (
                  <div key={key}>
                    <span>{key}</span>
                    <strong>{formatNumber(value, 5)}</strong>
                  </div>
                ))}
              </div>
            </>
          ) : (
            <div className="empty-state">Run a calculation to compare pricing models and Greeks.</div>
          )}
        </section>
      </section>

      <section className="panel analytics-panel">
        <div className="panel-heading">
          <PieChart size={20} />
          <h2>Usage Analytics</h2>
        </div>

        {analytics && analytics.totalRequests > 0 ? (
          <div className="analytics-layout">
            <div className="stat-column">
              <div className="metric">
                <span>Requests priced</span>
                <strong>{formatNumber(analytics.totalRequests, 0)}</strong>
              </div>
              <div className="metric">
                <span>Unique tickers</span>
                <strong>{formatNumber(analytics.uniqueTickers, 0)}</strong>
              </div>
              <div className="metric" title="Average gap between the highest and lowest of the three model prices">
                <span>Avg model spread</span>
                <strong>${formatNumber(analytics.averageModelSpread)}</strong>
              </div>
            </div>

            <figure className="bar-chart">
              <figcaption>Requests by ticker</figcaption>
              {tickerCounts.map(([name, count]) => (
                <div className="bar-row" key={name} title={`${name}: ${count} requests`}>
                  <span className="bar-label">{name}</span>
                  <div className="bar-track">
                    <div className="bar-fill" style={{ width: `${(count / busiestCount) * 100}%` }} />
                  </div>
                  <span className="bar-value">{count}</span>
                </div>
              ))}
              {analytics.uniqueTickers > tickerCounts.length && (
                <p className="bar-note">Showing the {tickerCounts.length} most requested of {analytics.uniqueTickers} tickers.</p>
              )}
            </figure>
          </div>
        ) : (
          <div className="empty-state">Analytics appear once pricing requests have been saved.</div>
        )}
      </section>

      <section className="panel history-panel">
        <div className="panel-heading">
          <Database size={20} />
          <h2>Recent Requests</h2>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Ticker</th>
                <th>Type</th>
                <th>Strike</th>
                <th>Black-Scholes</th>
                <th>Monte Carlo</th>
                <th>Binomial</th>
                <th>IV</th>
              </tr>
            </thead>
            <tbody>
              {history.map((row) => (
                <tr key={row.id}>
                  <td>{row.ticker}</td>
                  <td>{row.optionType}</td>
                  <td>${formatNumber(row.strike, 2)}</td>
                  <td>${formatNumber(row.blackScholesPrice, 2)}</td>
                  <td>${formatNumber(row.monteCarloPrice, 2)}</td>
                  <td>${formatNumber(row.binomialTreePrice, 2)}</td>
                  <td>{row.impliedVolatility ? `${formatNumber(row.impliedVolatility * 100, 2)}%` : "—"}</td>
                </tr>
              ))}
              {!history.length && (
                <tr>
                  <td colSpan={7}>No saved pricing requests yet.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </main>
  );
}

createRoot(document.getElementById("root")!).render(<App />);
