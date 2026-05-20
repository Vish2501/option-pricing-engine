# Option Pricing Frontend

React + Vite UI for the Spring Boot options-pricing backend.

## Run

Start the backend first:

```bash
cd /Users/vish/option-pricing-engine/backend
docker compose up -d
mvn spring-boot:run
```

Then start the frontend:

```bash
cd /Users/vish/option-pricing-engine/frontend
npm install
npm run dev
```

Open:

```text
http://localhost:5173
```

The app calls the versioned backend API at `/api/v1`. Vite proxies `/api` to `http://localhost:8080`.

If the backend has `API_KEY` configured, add:

```text
VITE_API_KEY=your-history-api-key
```

to `frontend/.env.local`.
