import { PieChart } from "lucide-react";
import { formatNumber } from "../format";
import type { Analytics } from "../types";

const MAX_TICKER_BARS = 8;

export function AnalyticsPanel({ analytics }: { analytics: Analytics | null }) {
  // The API already returns tickers busiest-first; show the top few as bars.
  const tickerCounts = Object.entries(analytics?.requestsByTicker ?? {}).slice(0, MAX_TICKER_BARS);
  const busiestCount = tickerCounts.length ? tickerCounts[0][1] : 0;

  return (
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
  );
}
