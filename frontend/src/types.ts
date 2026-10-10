export type OptionType = "CALL" | "PUT";

export type Greeks = {
  delta: number;
  gamma: number;
  vega: number;
  theta: number;
  rho: number;
};

export type PricingRequest = {
  ticker: string;
  spotPrice: number | null;
  strike: number;
  expiry: string;
  optionType: OptionType;
  volatility: number | null;
  riskFreeRate: number | null;
  dividendYield: number | null;
  marketPrice: number | null;
};

export type PricingResponse = {
  ticker: string;
  spotPrice: number;
  strike: number;
  expiry: string;
  timeToMaturityYears: number;
  optionType: OptionType;
  riskFreeRate: number;
  dividendYield: number;
  volatility: number;
  blackScholesPrice: number;
  monteCarloPrice: number;
  monteCarloStandardError: number;
  binomialTreePrice: number;
  americanBinomialPrice: number;
  earlyExercisePremium: number;
  impliedVolatility: number | null;
  greeks: Greeks;
};

export type MarketData = {
  ticker: string;
  livePrice: number;
  historicalVolatility: number;
};

// Call and put on the same stock, struck at the current price and expiring in 30 days.
export type Comparison = {
  ticker: string;
  spotPrice: number;
  historicalVolatility: number;
  call: PricingResponse;
  put: PricingResponse;
};

// A saved request. Holds the three European model prices, not the per-request extras.
export type HistoryRow = {
  id: number;
  ticker: string;
  spotPrice: number;
  strike: number;
  expiry: string;
  optionType: OptionType;
  riskFreeRate: number;
  dividendYield: number;
  volatility: number;
  blackScholesPrice: number;
  monteCarloPrice: number;
  binomialTreePrice: number;
  impliedVolatility: number | null;
  requestedAt: string;
};

export type HistoryPage = {
  rows: HistoryRow[];
  total: number;
};

export type TickerSuggestion = {
  symbol: string;
  name: string;
  exchange: string;
};

export type Analytics = {
  totalRequests: number;
  uniqueTickers: number;
  requestsByTicker: Record<string, number>;
  averageBlackScholesPrice: number;
  averageMonteCarloPrice: number;
  averageBinomialTreePrice: number;
  averageModelSpread: number;
};
