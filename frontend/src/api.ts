import type {
  Analytics,
  Comparison,
  HistoryPage,
  HistoryRow,
  MarketData,
  PricingRequest,
  PricingResponse,
  TickerSuggestion
} from "./types";

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "/api/v1";

async function request(url: string, init?: RequestInit): Promise<Response> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${url}`, init);
  } catch {
    throw new Error("The server is still waking up. Please try again in a minute.");
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.message ?? `Request failed with ${response.status}`);
  }
  return response;
}

async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  return (await request(url, init)).json();
}

export function priceOption(payload: PricingRequest) {
  return requestJson<PricingResponse>("/price", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

export function fetchMarketData(symbol: string) {
  return requestJson<MarketData>(`/stock/${encodeURIComponent(symbol.trim())}`);
}

export function fetchComparison(symbol: string) {
  return requestJson<Comparison>(`/price/compare/${encodeURIComponent(symbol.trim())}`);
}

export function searchTickers(query: string) {
  return requestJson<TickerSuggestion[]>(`/tickers/search?q=${encodeURIComponent(query)}`);
}

export function fetchAnalytics() {
  return requestJson<Analytics>("/history/analytics");
}

export async function fetchHistory(options: { ticker: string; page: number; size: number }): Promise<HistoryPage> {
  const params = new URLSearchParams({ page: String(options.page), size: String(options.size) });
  if (options.ticker) params.set("ticker", options.ticker);

  const response = await request(`/history?${params}`);
  const rows: HistoryRow[] = await response.json();
  // The total comes back in a header so the body can stay a plain list.
  const total = Number(response.headers.get("X-Total-Count") ?? rows.length);
  return { rows, total };
}
