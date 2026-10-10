# Option Pricing Frontend

React + Vite UI for the Spring Boot options-pricing backend.

## Run

Start the backend first:

```bash
cd backend
docker compose up -d
mvn spring-boot:run
```

Then start the frontend:

```bash
cd frontend
npm install
npm run dev
```

Open:

```text
http://localhost:5173
```

The app calls the versioned backend API at `/api/v1`. Vite proxies `/api` to `http://localhost:8080`.

To point the app at a deployed backend instead, add:

```text
VITE_API_BASE_URL=https://option-pricing-api.onrender.com/api/v1
```

to `frontend/.env.local`.

The app sends no API key. Anything bundled into a browser app is readable by every visitor,
so the backend protects itself with rate limiting instead.

## Vercel Deployment

Use:

```text
Root Directory: frontend
Build Command: npm run build
Output Directory: dist
```

Set:

```text
VITE_API_BASE_URL=https://<your-render-service>.onrender.com/api/v1
```

## Checks

```bash
npm run lint    # ESLint
npm test        # Vitest unit and component tests
npm run build   # type-check and production build
```

## Layout

```text
src/
├── App.tsx              # page shell; owns the result, analytics and refresh state
├── api.ts               # every call to the backend
├── types.ts             # request and response shapes
├── format.ts            # number and percentage formatting
├── useWakeNotice.ts     # flags slow requests while the server cold-starts
└── components/
    ├── PricingForm.tsx      # inputs, ticker search, market-data lookup
    ├── ResultsPanel.tsx     # model prices and Greeks
    ├── AnalyticsPanel.tsx   # usage tiles and requests-by-ticker chart
    └── HistoryPanel.tsx     # saved requests with filter and paging
```
