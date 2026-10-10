import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import type { Analytics, PricingResponse } from "../types";
import { AnalyticsPanel } from "./AnalyticsPanel";
import { ResultsPanel } from "./ResultsPanel";

afterEach(cleanup);

const result: PricingResponse = {
  ticker: "AAPL",
  spotPrice: 226.5,
  strike: 240,
  expiry: "2026-11-09",
  timeToMaturityYears: 0.08,
  optionType: "PUT",
  riskFreeRate: 0.05,
  dividendYield: 0,
  volatility: 0.2191,
  blackScholesPrice: 14.1045,
  monteCarloPrice: 13.9952,
  monteCarloStandardError: 0.1,
  binomialTreePrice: 14.1052,
  americanBinomialPrice: 14.3859,
  earlyExercisePremium: 0.2807,
  impliedVolatility: 0.5709,
  greeks: { delta: -0.79527, gamma: 0.01995, vega: 18.4354, theta: -5.1, rho: -15.2 }
};

describe("ResultsPanel", () => {
  it("prompts for a calculation before there is a result", () => {
    render(<ResultsPanel result={null} marketPriceEntered={false} />);

    expect(screen.getByText(/Run a calculation/)).toBeTruthy();
  });

  it("shows the model prices, confidence interval and early exercise premium", () => {
    render(<ResultsPanel result={result} marketPriceEntered />);

    expect(screen.getByText("$14.1045")).toBeTruthy();
    expect(screen.getByText("95% confidence: ±$0.196")).toBeTruthy();
    expect(screen.getByText("Early exercise adds $0.2807")).toBeTruthy();
    expect(screen.getByText("57.09%")).toBeTruthy();
  });

  it("explains a missing implied volatility when a market price was entered", () => {
    render(<ResultsPanel result={{ ...result, impliedVolatility: null }} marketPriceEntered />);

    expect(screen.getByText("No volatility can produce this market price")).toBeTruthy();
  });

  it("asks for a market price when none was entered", () => {
    render(<ResultsPanel result={{ ...result, impliedVolatility: null }} marketPriceEntered={false} />);

    expect(screen.getByText("Enter a market price to solve for it")).toBeTruthy();
  });
});

describe("AnalyticsPanel", () => {
  const analytics: Analytics = {
    totalRequests: 20,
    uniqueTickers: 2,
    requestsByTicker: { AAPL: 15, MSFT: 5 },
    averageBlackScholesPrice: 12,
    averageMonteCarloPrice: 12.1,
    averageBinomialTreePrice: 12,
    averageModelSpread: 0.0712
  };

  it("shows an empty state when nothing has been priced", () => {
    render(<AnalyticsPanel analytics={null} />);

    expect(screen.getByText(/Analytics appear once/)).toBeTruthy();
  });

  it("draws one bar per ticker, scaled to the busiest", () => {
    const { container } = render(<AnalyticsPanel analytics={analytics} />);

    const widths = [...container.querySelectorAll<HTMLElement>(".bar-fill")].map((bar) => bar.style.width);
    expect(widths.map((width) => Math.round(parseFloat(width)))).toEqual([100, 33]);
    expect(screen.getByText("AAPL")).toBeTruthy();
    expect(screen.getByText("$0.0712")).toBeTruthy();
  });
});
