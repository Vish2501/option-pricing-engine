import { useEffect, useState } from "react";
import { Loader2, RefreshCw } from "lucide-react";
import { fetchAnalytics } from "./api";
import { AnalyticsPanel } from "./components/AnalyticsPanel";
import { HistoryPanel } from "./components/HistoryPanel";
import { PricingForm } from "./components/PricingForm";
import { ResultsPanel } from "./components/ResultsPanel";
import type { Analytics, PricingResponse } from "./types";
import { useWakeNotice } from "./useWakeNotice";

export function App() {
  const [result, setResult] = useState<PricingResponse | null>(null);
  const [marketPriceEntered, setMarketPriceEntered] = useState(false);
  const [analytics, setAnalytics] = useState<Analytics | null>(null);
  const [analyticsLoading, setAnalyticsLoading] = useState(false);
  // Bumped after each pricing and by the refresh button, so saved data is reloaded.
  const [refreshKey, setRefreshKey] = useState(0);
  const [error, setError] = useState("");
  const [waking, track] = useWakeNotice();

  useEffect(() => {
    let stale = false;
    setAnalyticsLoading(true);
    // This is the first request the page makes, so it is the one that wakes a sleeping server.
    track(fetchAnalytics())
      .then((summary) => !stale && setAnalytics(summary))
      .catch(() => !stale && setAnalytics(null))
      .finally(() => !stale && setAnalyticsLoading(false));
    return () => {
      stale = true;
    };
  }, [refreshKey, track]);

  function handlePriced(priced: PricingResponse, marketPriceWasEntered: boolean) {
    setResult(priced);
    setMarketPriceEntered(marketPriceWasEntered);
    setRefreshKey((key) => key + 1);
  }

  return (
    <main className="app-shell">
      <section className="topbar">
        <div>
          <h1>Options Pricing Engine</h1>
          <p>Black-Scholes, Monte Carlo, Binomial Tree, Greeks, implied volatility, and request history.</p>
        </div>
        <button className="icon-button" type="button" onClick={() => setRefreshKey((key) => key + 1)} title="Refresh saved data">
          {analyticsLoading ? <Loader2 className="spin" size={18} /> : <RefreshCw size={18} />}
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
        <PricingForm track={track} onPriced={handlePriced} onError={setError} />
        <ResultsPanel result={result} marketPriceEntered={marketPriceEntered} />
      </section>

      <AnalyticsPanel analytics={analytics} />
      <HistoryPanel refreshKey={refreshKey} />
    </main>
  );
}
