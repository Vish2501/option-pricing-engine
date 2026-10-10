# Option Pricing Backend

Spring Boot API for the options pricing engine: Black-Scholes, Monte Carlo and binomial-tree pricing, Greeks, implied volatility, Yahoo market data, and a PostgreSQL request log with SQL analytics. See the [project README](../README.md) for the overview and how it relates to the Python project it started from.

## Stack

- Java 17 (builds and runs on 17 or 21; Spring Boot 3.2 does not support newer versions)
- Spring Boot 3.2
- Maven
- Spring Web
- Spring Data JPA
- Spring Security
- Springdoc OpenAPI
- PostgreSQL
- Flyway migrations
- Apache Commons Math
- H2 for most integration tests, real PostgreSQL for one

## Project Structure

```text
src/main/java/com/optionpricing/
├── config
├── controller
├── dto
├── entity
├── model
├── repository
└── service
```

## Local Database

Create a PostgreSQL database:

```sql
CREATE DATABASE option_pricing;
```

Or start one with Docker:

```bash
docker compose up postgres -d
```

The app defaults to:

```text
jdbc:postgresql://localhost:5432/option_pricing
username: postgres
password:
```

Override with:

```bash
export DATABASE_URL=jdbc:postgresql://localhost:5432/option_pricing
export DATABASE_USERNAME=postgres
export DATABASE_PASSWORD=your_password
```

Render-style variables are also supported:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/database
export SPRING_DATASOURCE_USERNAME=database_user
export SPRING_DATASOURCE_PASSWORD=database_password
export CORS_ALLOWED_ORIGINS=https://your-vercel-app.vercel.app
```

The included Docker database uses `postgres` as the password.
## Access and rate limiting

The API is public and has no logins. It is protected by per-IP rate limiting, configured
under `rate-limit` in `application.yml`:

| Limit | Default |
|---|---|
| Any `/api` request, per client | 60 per minute |
| `POST /api/v1/price`, per client | 10 per minute |
| `POST /api/v1/price`, all clients combined | 300 per hour |

Over the limit, the API returns `429` with a `Retry-After` header. Counters are held in
memory, which suits a single instance. Clients are identified by `X-Forwarded-For`, which a
caller can forge; the combined hourly cap is the backstop for that.

`API_KEY` should stay unset for the public demo. If you set it, `GET /api/v1/history` and
`GET /api/v1/history/analytics` require `X-API-Key` or a bearer token. That only suits
server-to-server callers: the React app does not send a key, because anything shipped to a
browser is public.

## Run

```bash
mvn spring-boot:run
```

The API runs at `http://localhost:8080`.

## API

Swagger UI is available at:

```text
http://localhost:8080/swagger-ui.html
```

Health checks are available at:

```text
http://localhost:8080/health
```

`POST /api/v1/price`

```json
{
  "ticker": "AAPL",
  "spotPrice": 226.5,
  "strike": 207.5,
  "expiry": "2026-06-19",
  "optionType": "CALL",
  "volatility": 0.2191,
  "riskFreeRate": 0.05,
  "marketPrice": 22.25
}
```

`spotPrice`, `volatility`, `riskFreeRate`, `dividendYield` and `marketPrice` are optional. If spot or volatility are omitted, Yahoo chart data is used and volatility is annualized from the latest 30 daily log returns. The ticker must be one Yahoo recognises, even when every input is supplied.

Other endpoints:

```text
GET /api/v1/stock/{ticker}
GET /api/v1/tickers/search?q={name or symbol}
GET /api/v1/price/compare/{ticker}
GET /api/v1/history?ticker={symbol}&page={n}&size={1-100}
GET /api/v1/history/analytics
```

Every `/api/v1/price` request is saved to the `pricing_requests` table. `GET /history` returns
the newest first; `ticker`, `page` and `size` are optional, and the `X-Total-Count` response
header holds the number of matching rows.

Every error, whatever its cause, has the same shape:

```json
{ "message": "strike must not be null", "timestamp": "2026-10-10T15:22:51Z" }
```

Market data is cached in memory for 15 minutes per ticker.

## Production Profile

Use the `prod` profile when deploying:

```bash
SPRING_PROFILES_ACTIVE=prod mvn spring-boot:run
```

The production profile validates the database schema instead of mutating it automatically.

## Docker

Build and run the backend plus local Postgres:

```bash
docker compose up --build
```

The API will be available at:

```text
http://localhost:8080
```

## Render Deployment

Use these settings for a Docker Web Service:

```text
Name: option-pricing-api
Root Directory: backend
Dockerfile Path: Dockerfile
Health Check Path: /health
```

Required environment variables:

```text
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/<database>
SPRING_DATASOURCE_USERNAME=<user>
SPRING_DATASOURCE_PASSWORD=<password>
CORS_ALLOWED_ORIGINS=https://<your-vercel-app>.vercel.app
```

## Finance Notes

Black-Scholes gives the closed-form European option price and Greeks. The Greeks are returned in raw units:

| Greek | Meaning of one unit | To get the usual quoted figure |
|---|---|---|
| Delta | Price change per $1 move in the stock | as is |
| Gamma | Delta change per $1 move in the stock | as is |
| Vega | Price change per 1.00 (100 percentage points) of volatility | divide by 100 for per-point vega |
| Theta | Price change per year of time passing | divide by 365 for per-day theta |
| Rho | Price change per 1.00 (100 percentage points) of interest rate | divide by 100 for per-point rho |

Monte Carlo simulates terminal prices with Geometric Brownian Motion:

```text
S_T = S * exp((r - 0.5 * sigma^2) * T + sigma * sqrt(T) * Z)
```

The Binomial Tree uses the Cox-Ross-Rubinstein up/down model and works backward from terminal payoffs. For European options, it converges toward Black-Scholes as the step count increases.

The same tree also prices American exercise: at every node the value is the larger of holding on and exercising immediately. The response returns this as `americanBinomialPrice`, with `earlyExercisePremium` as its difference from the European tree price.

Monte Carlo returns `monteCarloStandardError` alongside its price. The simulation uses a fixed seed so the same inputs always give the same answer; the standard error reports how far that single draw could be from the true price.

Implied volatility is solved from a market option price using Newton-Raphson first, then bisection as a fallback. Before solving, the market price is checked against no-arbitrage bounds: an option cannot be worth less than its value at zero volatility or more than its value at unlimited volatility. Outside those bounds no volatility fits, and `impliedVolatility` is returned as `null`.

## Test

```bash
mvn test
```

One test, `PostgresIntegrationTest`, runs the Flyway migrations and the aggregate queries
against a real PostgreSQL database. It is skipped unless `POSTGRES_TEST_URL` is set:

```bash
export POSTGRES_TEST_URL=jdbc:postgresql://localhost:5432/option_pricing_test
export POSTGRES_TEST_USERNAME=postgres
export POSTGRES_TEST_PASSWORD=postgres
mvn test
```

CI always runs it.

Tests include the Python project sample report values:

```text
AAPL, S=226.5, K=207.5, T=7 days, r=5%, sigma=21.91%
Black-Scholes ~= 19.17
Binomial Tree ~= 19.17
Implied Volatility ~= 82.16%
```
