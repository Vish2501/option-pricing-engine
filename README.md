# Options Pricing Engine

Full-stack options-pricing project built with Java, Spring Boot, PostgreSQL, REST APIs, and React.

The backend is inspired by the Python/Streamlit project [saeedbidi/option_pricing](https://github.com/saeedbidi/option_pricing), but implemented as a production-style Java REST API with a separate React frontend.

## Features

- Black-Scholes pricing for call and put options
- Monte Carlo simulation using Geometric Brownian Motion, with a standard error on the estimate
- Cox-Ross-Rubinstein Binomial Tree pricing for European and American exercise
- Greeks: Delta, Gamma, Vega, Theta, Rho
- Implied volatility with Newton-Raphson and bisection fallback
- Yahoo Finance live price lookup
- Historical volatility from recent daily close prices
- PostgreSQL logging of pricing requests and model outputs
- Flyway database migration for deployable schema setup
- Versioned REST API with Swagger/OpenAPI documentation
- Per-IP rate limiting on the public API, with a tighter limit on pricing requests
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
```

Render-style variable names are supported too:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/database
export SPRING_DATASOURCE_USERNAME=database_user
export SPRING_DATASOURCE_PASSWORD=database_password
export CORS_ALLOWED_ORIGINS=https://your-vercel-app.vercel.app
```

The API is public and has no logins; it is protected by per-IP rate limiting (60 requests a
minute, 10 pricing requests a minute, returning `429` beyond that). See
[backend/README.md](backend/README.md) for the limits and how to change them.

`API_KEY` should stay unset for the public demo. If you set it, `GET /api/v1/history` and
`GET /api/v1/history/analytics` require `X-API-Key` or a bearer token. That only suits
server-to-server callers: the React app does not send a key, because anything shipped to a
browser is public.

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
To use a deployed backend instead, create `frontend/.env.local` with:

```text
VITE_API_BASE_URL=https://option-pricing-api.onrender.com/api/v1
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
Health checks are available at `http://localhost:8080/health`.

## Render + Vercel Deployment

Backend on Render:

```text
Name: option-pricing-api
Language: Docker
Root Directory: backend
Dockerfile Path: Dockerfile
Health Check Path: /health
```

Set these Render environment variables:

```text
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/<database>
SPRING_DATASOURCE_USERNAME=<user>
SPRING_DATASOURCE_PASSWORD=<password>
CORS_ALLOWED_ORIGINS=https://<your-vercel-app>.vercel.app
```

Frontend on Vercel:

```text
Root Directory: frontend
Build Command: npm run build
Output Directory: dist
```

For local Docker smoke testing:

```bash
cd backend
docker compose up --build
```

Set these Vercel environment variables:

```text
VITE_API_BASE_URL=https://<your-render-service>.onrender.com/api/v1
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

## Production Checklist

- **Database:** PostgreSQL-backed request log with explicit columns and indexes on ticker and request time.
- **REST API:** Versioned `/api/v1` endpoints, correct HTTP statuses, DTOs, validation, and Swagger docs.
- **Design patterns:** Controller, service, repository, DTO, and pricing-strategy style services for each model.
- **Error handling:** Central exception handler with structured JSON errors.
- **Security:** No checked-in secrets and no secrets in the frontend bundle; the public API is rate limited per IP.
- **Testing:** Unit tests for pricing math and integration tests for API validation, persistence, and rate limiting.
- **Deployment:** Environment-variable configuration for Render/Vercel deployments; Flyway prepares fresh Postgres databases.
- **Code quality:** Clean repo structure, focused README, and reproducible Maven/Vite build commands.

## Notes

The three pricing models should be close for European options. Black-Scholes and Binomial Tree should converge closely, while Monte Carlo should be close but not identical because it uses random simulation. Its standard error says how close: the true price is within about 1.96 standard errors of the estimate 95% of the time.

The binomial tree is also run with American exercise, which allows exercising before expiry. The difference from the European price is the early-exercise premium: zero for a call on a stock that pays no dividends, positive for puts.
