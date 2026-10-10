export function formatNumber(value: number | null | undefined, digits = 4) {
  if (value === null || value === undefined || Number.isNaN(value)) return "—";
  return new Intl.NumberFormat("en-US", {
    maximumFractionDigits: digits,
    minimumFractionDigits: Math.min(2, digits)
  }).format(value);
}

export function formatPercent(fraction: number | null | undefined) {
  if (fraction === null || fraction === undefined || Number.isNaN(fraction)) return "—";
  return `${formatNumber(fraction * 100, 2)}%`;
}
