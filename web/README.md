# Eunomia Web

React 18 + Vite + TypeScript + Tailwind frontend for the Eunomia
compliance engine. State management via Zustand (auth/tenant context)
and TanStack Query (server data caching).

## Local development

```bash
# from web/
npm install
npm run dev
```

Dev server runs on `http://localhost:5173` and proxies `/api/**` (and
`/ws/**`) to the Spring Boot backend on `:8080`, so no CORS dance.

Start the backend first:

```bash
# from project root
docker compose up -d postgres
./gradlew bootRun
```

## Build

```bash
npm run build
```

Produces `dist/`. The Spring Boot Docker image picks this up and serves
it as static content (wired in Sprint 1).

## Lint + format

```bash
npm run lint
npm run format
```

## Project structure

```
src/
  ├── pages/    # route-level components
  ├── lib/      # API client, helpers
  ├── stores/   # Zustand stores (auth, tenant context)
  ├── styles/   # Tailwind base styles
  ├── App.tsx   # router
  └── main.tsx  # entry point
```
