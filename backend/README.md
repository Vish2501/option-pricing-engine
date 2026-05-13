# Option Pricing Backend

Spring Boot API inspired by [saeedbidi/option_pricing](https://github.com/saeedbidi/option_pricing). It ports the core Python/Streamlit finance workflow into a Java backend: Yahoo market data, historical volatility, Black-Scholes, Monte Carlo, Binomial Tree, Greeks, implied volatility, and PostgreSQL request logging.

## Stack

- Java 17
- Spring Boot 3.2
- Maven
- Spring Web
- Spring Data JPA
- PostgreSQL
- Apache Commons Math
- H2 for integration tests

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
docker compose up -d
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

The included Docker database uses `postgres` as the password.

## Run

```bash
mvn spring-boot:run
```

The API runs at `http://localhost:8080`.

## API

`POST /api/price`

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

`spotPrice`, `volatility`, `riskFreeRate`, and `marketPrice` are optional. If spot or volatility are omitted, Yahoo chart data is used and volatility is annualized from the latest 30 daily log returns.

Other endpoints:

```text
GET /api/stock/{ticker}
GET /api/price/compare/{ticker}
GET /api/history
GET /api/history/analytics
```

Every `/api/price` request is saved to the `pricing_requests` table.

## Finance Notes

Black-Scholes gives the closed-form European option price and Greeks. Vega, Theta, and Rho are returned in the same raw annual convention used by the referenced Python report.

Monte Carlo simulates terminal prices with Geometric Brownian Motion:

```text
S_T = S * exp((r - 0.5 * sigma^2) * T + sigma * sqrt(T) * Z)
```

The Binomial Tree uses the Cox-Ross-Rubinstein up/down model and works backward from terminal payoffs. For European options, it converges toward Black-Scholes as the step count increases.

Implied volatility is solved from a market option price using Newton-Raphson first, then bisection as a fallback.

## Test

```bash
mvn test
```

Tests include the Python project sample report values:

```text
AAPL, S=226.5, K=207.5, T=7 days, r=5%, sigma=21.91%
Black-Scholes ~= 19.17
Binomial Tree ~= 19.17
Implied Volatility ~= 82.16%
```
