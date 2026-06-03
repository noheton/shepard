# e2e — Playwright end-to-end test suite

## Running the tests

```bash
cd e2e
npm install
npx playwright test
```

By default tests run against `https://shepard.nuclide.systems` (configured in
`playwright.config.ts`).

## Environment variables for live Keycloak auth

Playwright specs that call `loginAs()` use these env vars:

| Variable | Default | Description |
|---|---|---|
| `DEMO_USER` | `flodemo` | Keycloak username for test user |
| `DEMO_PASSWORD` | `flo-demo` | Keycloak password for test user |

Set them to match a provisioned user on your target Keycloak realm:

```bash
export DEMO_USER=alice
export DEMO_PASSWORD=alice-demo
npx playwright test
```

The live `shepard-auth.nuclide.systems` realm currently provisions `alice`/`alice-demo`
(user role). If `flodemo` is not provisioned on your realm, set these variables
before running.

Both constants are exported from `tests/helpers/auth.ts` so spec files import
them instead of duplicating the `process.env` fallback pattern:

```ts
import { loginAs, DEMO_USER, DEMO_PASSWORD } from "./helpers/auth";

await loginAs(page, DEMO_USER, DEMO_PASSWORD);
```

## Other environment variables

| Variable | Default | Description |
|---|---|---|
| `KEYCLOAK_HOST` | `https://shepard-auth.nuclide.systems` | Keycloak base URL |
| `BACKEND_URL` | `https://shepard-api.nuclide.systems` | Shepard backend API URL |
