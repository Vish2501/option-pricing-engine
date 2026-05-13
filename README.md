# Options Pricing Engine

Full-stack options-pricing project built with Java, Spring Boot, PostgreSQL, REST APIs, and React.

The backend is inspired by the Python/Streamlit project [saeedbidi/option_pricing](https://github.com/saeedbidi/option_pricing), but implemented as a production-style Java REST API with a separate React frontend.

## Features

- Black-Scholes pricing for call and put options
- Monte Carlo simulation using Geometric Brownian Motion
- Cox-Ross-Rubinstein Binomial Tree pricing
- Greeks: Delta, Gamma, Vega, Theta, Rho
- Implied volatility with Newton-Raphson and bisection fallback
- Yahoo Finance live price lookup
- Historical volatility from recent daily close prices
- PostgreSQL logging of pricing requests and model outputs
- React dashboard for pricing, Greeks, implied volatility, and history

## Project Structure

```text
option-pricing-engine/
├── backend/    # Spring Boot API
└── frontend/   # React + Vite UI
```

## Run Backend

```bash
cd backend
mvn spring-boot:run
```

The backend runs on:

```text
http://localhost:8080
```

By default it expects PostgreSQL:

```text
database: option_pricing
username: postgres
password: empty
```

If you have Docker installed:

```bash
cd backend
docker compose up -d
```

Or configure your local database:

```bash
export DATABASE_URL=jdbc:postgresql://localhost:5432/option_pricing
export DATABASE_USERNAME=postgres
export DATABASE_PASSWORD=your_password
```

## Run Frontend

```bash
cd frontend
npm install
npm run dev
```

Open:

```text
http://localhost:5173
```

The frontend proxies `/api` requests to `http://localhost:8080`.

## API Endpoints

```text
POST /api/price
GET /api/stock/{ticker}
GET /api/price/compare/{ticker}
GET /api/history
GET /api/history/analytics
```

Example pricing request:

```json
{
  "ticker": "AAPL",
  "spotPrice": 100,
  "strike": 110,
  "expiry": "2026-06-09",
  "optionType": "CALL",
  "volatility": 0.2191,
  "riskFreeRate": 0.05,
  "marketPrice": 0.25
}
```

## Test

Backend:

```bash
cd backend
mvn test
```

Frontend:

```bash
cd frontend
npm run build
```

## Notes

The three pricing models should be close for European options. Black-Scholes and Binomial Tree should converge closely, while Monte Carlo should be close but not identical because it uses random simulation.
