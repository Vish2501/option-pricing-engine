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
- Versioned REST API with Swagger/OpenAPI documentation
- Optional API-key protection for history and analytics endpoints
- DTO validation, global error responses, and integration tests
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
export API_KEY=your-history-api-key
```

`API_KEY` is optional for local demos. When set, `GET /api/v1/history` and
`GET /api/v1/history/analytics` require `X-API-Key` or a bearer token.

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
If the backend uses `API_KEY`, create `frontend/.env.local` with:

```text
VITE_API_KEY=your-history-api-key
```

## API Endpoints

```text
POST /api/v1/price
GET /api/v1/stock/{ticker}
GET /api/v1/price/compare/{ticker}
GET /api/v1/history
GET /api/v1/history/analytics
```

Swagger UI is available at `http://localhost:8080/swagger-ui.html`.

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

## Production Checklist

- **Database:** PostgreSQL-backed request log with explicit columns and indexes on ticker and request time.
- **REST API:** Versioned `/api/v1` endpoints, correct HTTP statuses, DTOs, validation, and Swagger docs.
- **Design patterns:** Controller, service, repository, DTO, and pricing-strategy style services for each model.
- **Error handling:** Central exception handler with structured JSON errors.
- **Security:** No checked-in secrets; optional API-key protection for historical data endpoints.
- **Testing:** Unit tests for pricing math and integration tests for API validation, persistence, and security behavior.
- **Deployment:** Environment-variable configuration for Railway/Vercel-style deployments; frontend can pass `VITE_API_KEY`.
- **Code quality:** Clean repo structure, focused README, and reproducible Maven/Vite build commands.

## Notes

The three pricing models should be close for European options. Black-Scholes and Binomial Tree should converge closely, while Monte Carlo should be close but not identical because it uses random simulation.
