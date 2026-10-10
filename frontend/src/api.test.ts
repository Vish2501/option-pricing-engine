import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchHistory, fetchMarketData } from "./api";

function respondWith(body: unknown, init: ResponseInit = {}) {
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(body), init));
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("api", () => {
  it("surfaces the server's error message", async () => {
    respondWith({ message: "Invalid ticker or no Yahoo Finance data available: NVIDIA" }, { status: 400 });

    await expect(fetchMarketData("NVIDIA")).rejects.toThrow("Invalid ticker or no Yahoo Finance data available: NVIDIA");
  });

  it("falls back to the status code when the error has no JSON body", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("upstream down", { status: 502 })));

    await expect(fetchMarketData("AAPL")).rejects.toThrow("Request failed with 502");
  });

  it("explains a request that never reached the server", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));

    await expect(fetchMarketData("AAPL")).rejects.toThrow("The server is still waking up");
  });

  it("encodes the symbol into the path", async () => {
    const fetchMock = respondWith({ ticker: "BRK.B", livePrice: 1, historicalVolatility: 0.1 });

    await fetchMarketData(" brk.b ");

    expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/stock/brk.b");
  });

  it("reads the history total from the X-Total-Count header", async () => {
    const fetchMock = respondWith([{ id: 1 }, { id: 2 }], { headers: { "X-Total-Count": "27" } });

    const page = await fetchHistory({ ticker: "AAPL", page: 1, size: 2 });

    expect(page.total).toBe(27);
    expect(page.rows).toHaveLength(2);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/history?page=1&size=2&ticker=AAPL");
  });

  it("leaves the ticker out of the query when there is no filter", async () => {
    const fetchMock = respondWith([]);

    await fetchHistory({ ticker: "", page: 0, size: 8 });

    expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/history?page=0&size=8");
  });
});
