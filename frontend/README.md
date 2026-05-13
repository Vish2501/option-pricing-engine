# Option Pricing Frontend

React + Vite UI for the Spring Boot options-pricing backend.

## Run

Start the backend first:

```bash
cd /Users/vish/option-pricing-backend
docker compose up -d
mvn spring-boot:run
```

Then start the frontend:

```bash
cd /Users/vish/option-pricing-frontend
npm install
npm run dev
```

Open:

```text
http://localhost:5173
```

Vite proxies `/api` to `http://localhost:8080`.
