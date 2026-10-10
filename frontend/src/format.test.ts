import { describe, expect, it } from "vitest";
import { formatNumber, formatPercent } from "./format";

describe("formatNumber", () => {
  it("keeps up to four decimals and at least two", () => {
    expect(formatNumber(19.17123456)).toBe("19.1712");
    expect(formatNumber(5)).toBe("5.00");
  });

  it("groups thousands and honours the digits argument", () => {
    expect(formatNumber(1234567.891, 2)).toBe("1,234,567.89");
    expect(formatNumber(47, 0)).toBe("47");
  });

  it("shows a dash for missing values", () => {
    expect(formatNumber(null)).toBe("—");
    expect(formatNumber(undefined)).toBe("—");
    expect(formatNumber(Number.NaN)).toBe("—");
  });
});

describe("formatPercent", () => {
  it("turns a fraction into a percentage", () => {
    expect(formatPercent(0.2191)).toBe("21.91%");
  });

  it("shows a dash when there is no value", () => {
    expect(formatPercent(null)).toBe("—");
  });
});
