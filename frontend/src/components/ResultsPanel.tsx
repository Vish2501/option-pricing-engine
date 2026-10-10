import { BarChart3 } from "lucide-react";
import { formatNumber, formatPercent } from "../format";
import type { Greeks, PricingResponse } from "../types";

// The API returns raw Greeks; these say what one unit of each means.
const GREEK_UNITS: Record<keyof Greeks, string> = {
  delta: "per $1 in the stock",
  gamma: "delta change per $1",
  vega: "per 1.00 of volatility",
  theta: "per year",
  rho: "per 1.00 of interest rate"
};

type Props = {
  result: PricingResponse | null;
  // Whether a market price was supplied, to explain a missing implied volatility.
  marketPriceEntered: boolean;
};

export function ResultsPanel({ result, marketPriceEntered }: Props) {
  return (
    <section className="panel results-panel">
      <div className="panel-heading">
        <BarChart3 size={20} />
        <h2>Model Outputs</h2>
      </div>

      {result ? (
        <>
          <div className="metric-grid">
            <div className="metric">
              <span>Black-Scholes</span>
              <strong>${formatNumber(result.blackScholesPrice)}</strong>
              <small>European, closed form</small>
            </div>
            <div className="metric">
              <span>Monte Carlo</span>
              <strong>${formatNumber(result.monteCarloPrice)}</strong>
              <small title="95% confidence interval: 1.96 standard errors either side of the estimate">
                95% confidence: ±${formatNumber(1.96 * result.monteCarloStandardError)}
              </small>
            </div>
            <div className="metric">
              <span>Binomial Tree</span>
              <strong>${formatNumber(result.binomialTreePrice)}</strong>
              <small>European exercise</small>
            </div>
            <div className="metric">
              <span>Binomial Tree, American</span>
              <strong>${formatNumber(result.americanBinomialPrice)}</strong>
              <small>Early exercise adds ${formatNumber(Math.max(result.earlyExercisePremium, 0))}</small>
            </div>
            <div className="metric">
              <span>Implied Volatility</span>
              <strong>{formatPercent(result.impliedVolatility)}</strong>
              <small>{impliedVolatilityNote(result, marketPriceEntered)}</small>
            </div>
          </div>

          <div className="greeks-grid">
            {(Object.keys(GREEK_UNITS) as (keyof Greeks)[]).map((greek) => (
              <div key={greek}>
                <span>{greek}</span>
                <strong>{formatNumber(result.greeks[greek], 5)}</strong>
                <small>{GREEK_UNITS[greek]}</small>
              </div>
            ))}
          </div>
        </>
      ) : (
        <div className="empty-state">Run a calculation to compare pricing models and Greeks.</div>
      )}
    </section>
  );
}

function impliedVolatilityNote(result: PricingResponse, marketPriceEntered: boolean) {
  if (result.impliedVolatility !== null) return "From the market price";
  if (!marketPriceEntered) return "Enter a market price to solve for it";
  return "No volatility can produce this market price";
}
