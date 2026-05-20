CREATE TABLE pricing_requests (
    id BIGSERIAL PRIMARY KEY,
    ticker VARCHAR(255) NOT NULL,
    spot_price DOUBLE PRECISION NOT NULL,
    strike DOUBLE PRECISION NOT NULL,
    expiry DATE NOT NULL,
    option_type VARCHAR(255) NOT NULL,
    risk_free_rate DOUBLE PRECISION NOT NULL,
    volatility DOUBLE PRECISION NOT NULL,
    black_scholes_price DOUBLE PRECISION NOT NULL,
    monte_carlo_price DOUBLE PRECISION NOT NULL,
    binomial_tree_price DOUBLE PRECISION NOT NULL,
    implied_volatility DOUBLE PRECISION,
    delta DOUBLE PRECISION NOT NULL,
    gamma DOUBLE PRECISION NOT NULL,
    vega DOUBLE PRECISION NOT NULL,
    theta DOUBLE PRECISION NOT NULL,
    rho DOUBLE PRECISION NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_pricing_requests_ticker ON pricing_requests (ticker);
CREATE INDEX idx_pricing_requests_requested_at ON pricing_requests (requested_at);
