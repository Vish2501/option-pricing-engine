import { useEffect, useState } from "react";
import { ChevronLeft, ChevronRight, Database } from "lucide-react";
import { fetchHistory } from "../api";
import { formatNumber, formatPercent } from "../format";
import type { HistoryPage } from "../types";

const PAGE_SIZE = 8;
const FILTER_DEBOUNCE_MS = 300;

type Props = {
  // Changes whenever the saved requests may have changed, to trigger a reload.
  refreshKey: number;
};

export function HistoryPanel({ refreshKey }: Props) {
  const [filterText, setFilterText] = useState("");
  const [ticker, setTicker] = useState("");
  const [page, setPage] = useState(0);
  const [history, setHistory] = useState<HistoryPage>({ rows: [], total: 0 });

  // Apply the filter a moment after typing stops, and go back to the first page.
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setTicker(filterText);
      setPage(0);
    }, FILTER_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [filterText]);

  useEffect(() => {
    let stale = false;
    fetchHistory({ ticker, page, size: PAGE_SIZE })
      .then((result) => !stale && setHistory(result))
      .catch(() => !stale && setHistory({ rows: [], total: 0 }));
    return () => {
      stale = true;
    };
  }, [ticker, page, refreshKey]);

  const first = history.total === 0 ? 0 : page * PAGE_SIZE + 1;
  const last = page * PAGE_SIZE + history.rows.length;
  const hasNext = last < history.total;

  return (
    <section className="panel history-panel">
      <div className="panel-heading history-heading">
        <Database size={20} />
        <h2>Recent Requests</h2>
        <input
          className="history-filter"
          aria-label="Filter by ticker"
          placeholder="Filter by ticker"
          value={filterText}
          // Keep only characters a ticker can contain.
          onChange={(event) => setFilterText(event.target.value.toUpperCase().replace(/[^A-Z0-9.-]/g, ""))}
        />
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Priced</th>
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
            {history.rows.map((row) => (
              <tr key={row.id}>
                <td>{new Date(row.requestedAt).toLocaleDateString(undefined, { day: "numeric", month: "short" })}</td>
                <td>{row.ticker}</td>
                <td>{row.optionType}</td>
                <td>${formatNumber(row.strike, 2)}</td>
                <td>${formatNumber(row.blackScholesPrice, 2)}</td>
                <td>${formatNumber(row.monteCarloPrice, 2)}</td>
                <td>${formatNumber(row.binomialTreePrice, 2)}</td>
                <td>{formatPercent(row.impliedVolatility)}</td>
              </tr>
            ))}
            {!history.rows.length && (
              <tr>
                <td colSpan={8}>{ticker ? `No saved requests for ${ticker}.` : "No saved pricing requests yet."}</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {history.total > 0 && (
        <div className="pager">
          <span>
            {first}–{last} of {history.total}
          </span>
          <button type="button" className="icon-button" onClick={() => setPage(page - 1)} disabled={page === 0} title="Newer requests">
            <ChevronLeft size={16} />
          </button>
          <button type="button" className="icon-button" onClick={() => setPage(page + 1)} disabled={!hasNext} title="Older requests">
            <ChevronRight size={16} />
          </button>
        </div>
      )}
    </section>
  );
}
