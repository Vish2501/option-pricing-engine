-- Dividend yield is an input to every price, so it belongs with the other inputs in the log.
-- Rows saved before this column existed were priced with the default yield of zero.
ALTER TABLE pricing_requests ADD COLUMN dividend_yield DOUBLE PRECISION NOT NULL DEFAULT 0;
