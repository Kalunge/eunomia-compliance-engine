# Eunomia Web

Vue 3 + Vite + TypeScript + Tailwind frontend for the Eunomia compliance
engine.

## Local development

```bash
# from web/
npm install
npm run dev
```

Dev server runs on `http://localhost:5173` and proxies `/api/**` (and
`/ws/**`) to the Spring Boot backend on `:8080`, so no CORS dance.

Make sure the backend is running first: `./gradlew bootRun` from the
project root after `docker compose up -d postgres`.

## Build

```bash
npm run build
```

Produces `dist/`. The Spring Boot Docker image will pick this up and
serve it as static content (added in Sprint 1).
