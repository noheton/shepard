---
stage: deployed
last-stage-change: 2026-05-23
---

# API-Level Integration Test Suite — Design

**Scope.** Define a pytest-based integration test suite that runs real HTTP
calls against a live shepard stack (not mocked), uses the seeded LUMEN showcase
dataset as the known-state oracle, and integrates into CI for post-deploy
drift detection.

**Status.** Implemented at `e2e/api/` — 22 test cases across 8 modules, GitHub Actions workflow at `.github/workflows/integration-tests.yml`, `make integration-test` local target.

**Snapshot date.** 2026-05-19.

**Originating items.** Identified gap: `infrastructure/smoke-test.sh` verifies
only HTTP-200/401 status paths — it cannot catch data-integrity regressions
(missing fields, wrong counts, broken predecessor chains). This design closes
that gap with an assertion-rich suite that could catch the real regressions this
fork has hit (stale image, missing REST class, migration crash, broken wire
format).

**Related docs.**
- `aidocs/ops/59-performance-testing-and-tuning.md` — companion perf-smoke
- `aidocs/ops/49-in-app-user-docs.md` — Playwright screenshot pipeline (browser
  layer, distinct from this)
- `aidocs/34-upstream-upgrade-path.md` — upgrade ledger; new `/v2/` endpoints
  must be documented there
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — feature status; update when
  this suite ships

---

## §1. Goal

The suite must verify:

1. **Seed data integrity** — after `seed.py` runs, the exact named entities exist
   with the exact attributes the seed wrote. Any regression in collection/DataObject
   CRUD, timeseries upload, or annotation creation is caught before users hit it.
2. **CRUD round-trips** — create a new entity, read it back, assert field equality,
   then delete it. The cluster is back in the seed state after the suite exits.
3. **Auth gating** — unauthenticated requests return `401`/`403`, not `404` (a `404`
   means the REST class is missing from the JAR — the exact regression we've hit
   before). Authenticated requests with wrong scope return `403`, not `500`.
4. **Error shapes** — `4xx` responses carry a JSON body with a `message` or
   `violations` field; no stack traces leak into public endpoints.
5. **Fork-specific surface** — `/v2/` endpoints introduced by this fork function
   correctly (feature toggles, container annotations, watched containers, chart-view
   persistence, instance identity).
6. **Pagination** — list endpoints return the correct total count and the items
   present in the seed; page/size parameters are respected.

The suite is **not** a browser/UI test (that is Playwright in `e2e/`), not a
unit test (that is JUnit in `backend/`), and not a load test (that is
`infrastructure/perf-smoke.sh`). It is the middle layer: real HTTP, real data,
no browser.

---

## §2. Tech Choice

### Recommendation: pytest + httpx, with shepard Python client for domain calls

**Primary:** `pytest` test runner with `httpx` for raw HTTP calls.

**Domain layer:** The existing `shepard` Python client
(`clients/python/shepard/`) wraps the generated `shepard_client` OpenAPI
package and exposes typed errors (`ShepardNotFound`, `ShepardForbidden`, etc.).
Use this client for happy-path CRUD assertions — it matches the seeder pattern
exactly (both `entrypoint.sh`-launched `seed.py` and the client use
`shepard_client` as the shared contract under test).

**Raw httpx for specific cases:**
- `/v2/` endpoints not yet in the generated OpenAPI client
- Auth-rejection tests where you intentionally want the raw HTTP status without
  the client's error-wrapping
- Error-shape assertions (inspect the raw JSON body)

**Rationale against alternatives:**

| Option | Why not |
|---|---|
| JUnit + RestAssured | Pulls JVM toolchain into a Python test; Quarkus testcontainers are a different concept (they boot the app inside the JVM test process — not applicable to post-deploy against a live remote stack). |
| Playwright / Selenium | Browser layer; too slow and fragile for data-integrity assertions; belongs in `e2e/` for UI coverage. |
| k6 / Locust | Load-focused; no assertion DSL for field-level data integrity. |
| Raw `curl` scripts | Unmanageable at 15+ test cases; no fixture lifecycle; hard to assert nested JSON fields. |
| Robot Framework | Higher ceremony; no advantage over pytest for this use case. |

**Dependency matrix:**

```
pytest >= 8.0
httpx >= 0.27        # async-capable; keep sync for simplicity in v1
python-dotenv        # load .env.integration from repo root at session start
shepard-client       # generated OpenAPI client (GitLab PyPI index)
shepard              # thin wrapper (clients/python/shepard/)
```

The suite has its own isolated `pyproject.toml` at `e2e/api/` so it does not
pollute the seeder's or client library's dependency trees.

---

## §3. Directory Layout

```
e2e/api/
├── pyproject.toml           # pytest + httpx + shepard-client
├── conftest.py              # session-scoped fixtures: auth, seeded IDs
├── .env.integration.example # template: BACKEND_URL, KC_URL, KC_ADMIN_USER, …
├── tests/
│   ├── test_collections.py
│   ├── test_data_objects.py
│   ├── test_timeseries.py
│   ├── test_file_containers.py
│   ├── test_annotations.py
│   ├── test_auth.py
│   ├── test_v2_features.py
│   └── test_error_shapes.py
└── helpers/
    └── auth.py              # token-fetch helpers (Keycloak ROPC + API-key flows)
```

The suite lives at the repo root alongside the existing `e2e/` Playwright
directory and `infrastructure/`. This keeps it discoverable without folding it
into `backend/src/test/` (which implies JVM tooling) or `clients/python/shepard/tests/`
(which already has unit tests that run with a stub client and must not require a
live server).

---

## §4. Fixture Layout

### 4.1 Session-scoped: authenticated httpx session + shepard.Client

```python
# e2e/api/conftest.py

import os
import pytest
import httpx
import shepard
from helpers.auth import fetch_api_key_via_keycloak

BACKEND_URL = os.getenv("BACKEND_URL", "http://localhost:8080/shepard/api")
BACKEND_ROOT = BACKEND_URL.replace("/shepard/api", "")

@pytest.fixture(scope="session")
def api_key() -> str:
    """Obtain an API key authenticated as the demo admin user.

    Strategy (in priority order):
    1. SHEPARD_API_KEY env var — fastest; set in CI secrets or .env.integration.
    2. Keycloak ROPC flow using KC_URL + KC_ADMIN_USER + KC_ADMIN_PASS +
       KC_CLIENT_ID — mirrors exactly what entrypoint.sh does, so no new
       auth path is introduced.

    Never uses the bootstrap token (one-shot, consumed by entrypoint.sh on
    first deploy; gone by the time the suite runs).
    """
    if key := os.getenv("SHEPARD_API_KEY"):
        return key
    return fetch_api_key_via_keycloak(
        backend_url=BACKEND_URL,
        kc_url=os.getenv("KC_URL", "http://localhost:8082"),
        realm=os.getenv("KC_REALM", "shepard-demo"),
        client_id=os.getenv("KC_CLIENT_ID", "frontend-dev"),
        username=os.getenv("KC_ADMIN_USER", "admin"),
        password=os.getenv("KC_ADMIN_PASS", "admin-demo"),
    )

@pytest.fixture(scope="session")
def client(api_key) -> shepard.Client:
    return shepard.Client(
        host=BACKEND_URL,
        apikey=api_key,
        verify_ssl=False,  # dev stack uses self-signed cert behind Caddy
    )

@pytest.fixture(scope="session")
def http(api_key) -> httpx.Client:
    """Raw httpx client with API-key auth for /v2/ and error-shape tests."""
    return httpx.Client(
        base_url=BACKEND_ROOT,
        headers={"apikey": api_key},
        verify=False,
        timeout=15.0,
    )

@pytest.fixture(scope="session")
def http_anon() -> httpx.Client:
    """Unauthenticated httpx client for auth-gating tests."""
    return httpx.Client(base_url=BACKEND_ROOT, verify=False, timeout=10.0)
```

### 4.2 Session-scoped: seeded collection IDs

```python
@pytest.fixture(scope="session")
def lumen_collection(client):
    """Locate the seeded LUMEN collection by its canonical name.

    Fails fast with a readable message if seed.py hasn't run — catches the
    'suite ran before seeder finished' footgun before any test fires.
    """
    from shepard_client import CollectionSearchBody, CollectionSearchParams
    name = "LUMEN-Inspired Hotfire Test Campaign — Q3 2024 (synthetic)"
    body = CollectionSearchBody(
        searchParams=CollectionSearchParams(
            query=f'{{"property":"name","value":"{name}","operator":"eq"}}'
        )
    )
    results = client.search.search_collections(body)
    matches = [c for c in (results.results or []) if c.name == name]
    if not matches:
        pytest.fail(
            f"Seed collection '{name}' not found. "
            "Run examples/lumen-showcase/seed.py before the integration suite."
        )
    return matches[0]

@pytest.fixture(scope="session")
def lumen_data_objects(client, lumen_collection):
    """All DataObjects in the LUMEN collection, indexed by name."""
    objs = client.dataobjects.get_all_data_objects(lumen_collection.id)
    return {o.name: o for o in (objs or [])}
```

### 4.3 Function-scoped: ephemeral CRUD fixtures

```python
@pytest.fixture()
def temp_collection(client):
    """Create a throwaway collection; delete it after the test."""
    from shepard_client import Collection
    coll = client.collections.create_collection(
        Collection(name="__integration-test-temp__")
    )
    yield coll
    try:
        client.collections.delete_collection(coll.id)
    except Exception:
        pass  # already deleted by the test itself
```

---

## §5. Auth Strategy

### 5.1 Primary: Keycloak ROPC flow (mirrors entrypoint.sh)

The seeder's `entrypoint.sh` uses the Resource Owner Password Credentials grant
(ROPC) against `POST /realms/{realm}/protocol/openid-connect/token` with
`grant_type=password`, `client_id`, `username`, `password`. It then calls
`POST /shepard/api/users/{sub}/apikeys` to mint a persistent API key.

The integration suite does the same in `helpers/auth.py`:

```python
# e2e/api/helpers/auth.py

import httpx

def fetch_api_key_via_keycloak(
    backend_url: str,
    kc_url: str,
    realm: str,
    client_id: str,
    username: str,
    password: str,
) -> str:
    """Mint a fresh API key for the given Keycloak user.

    1. ROPC token grant from Keycloak.
    2. Decode the JWT sub (same base64 trick as entrypoint.sh).
    3. POST /shepard/api/users/{sub}/apikeys with Bearer auth.
    4. Return the 'jwt' field from the response.
    """
    import base64, json as _json

    # Step 1: ROPC
    resp = httpx.post(
        f"{kc_url}/realms/{realm}/protocol/openid-connect/token",
        data={
            "grant_type": "password",
            "client_id": client_id,
            "username": username,
            "password": password,
            "scope": "openid",
        },
        verify=False,
        timeout=15,
    )
    resp.raise_for_status()
    access_token = resp.json()["access_token"]

    # Step 2: decode sub from JWT payload (no signature verification needed)
    payload_b64 = access_token.split(".")[1]
    pad = payload_b64 + "=" * (-len(payload_b64) % 4)
    sub = _json.loads(base64.urlsafe_b64decode(pad))["sub"]

    # Step 3: first-touch to create the :User node (UserFilter on first request)
    httpx.get(
        f"{backend_url}/users/{sub}",
        headers={"Authorization": f"Bearer {access_token}"},
        verify=False,
        timeout=10,
    )

    # Step 4: mint API key
    key_resp = httpx.post(
        f"{backend_url}/users/{sub}/apikeys",
        headers={
            "Authorization": f"Bearer {access_token}",
            "Content-Type": "application/json",
        },
        json={"name": "integration-test-key"},
        verify=False,
        timeout=10,
    )
    key_resp.raise_for_status()
    return key_resp.json()["jwt"]
```

### 5.2 Faster CI path: `SHEPARD_API_KEY` secret

For CI runs where the full Keycloak stack is not available (e.g. the suite runs
against `https://shepard.box` from a scheduled workflow), store a long-lived
API key as a GitHub Actions secret `SHEPARD_IT_API_KEY`. The `api_key` fixture
reads this first and skips the ROPC flow entirely.

### 5.3 Bootstrap token: not used here

The bootstrap token (`/opt/shepard/.bootstrap-token` on the host;
`/opt/shepard-bootstrap/.bootstrap-token` inside the seeder volume) is a
one-shot credential consumed by `entrypoint.sh` on the first deploy. By the
time the integration suite runs the token has been consumed and the file either
no longer exists or is invalid. Do not design around it.

---

## §6. Specific Test Cases

The table below defines the minimum set. Each row maps to exactly one
`test_*` function. Expand within each module as coverage warrants.

| # | Module | Test function | What it asserts |
|---|---|---|---|
| 1 | `test_collections.py` | `test_lumen_collection_exists` | `lumen_collection.name` equals the exact constant; `description` contains "synthetic showcase" |
| 2 | `test_collections.py` | `test_lumen_collection_is_public` | `GET /collections/{id}/permissions` → `permissionType == "PUBLIC"` |
| 3 | `test_collections.py` | `test_crud_round_trip` | create `__it-temp__` → read by id → name matches → delete → `GET` returns 404 |
| 4 | `test_collections.py` | `test_list_includes_lumen` | `GET /collections?page=0&size=200` response contains a collection with the LUMEN name |
| 5 | `test_data_objects.py` | `test_fifteen_runs_exist` | `get_all_data_objects(lumen_collection.id)` returns exactly 15 top-level TR-00x objects |
| 6 | `test_data_objects.py` | `test_hold_days_not_fired` | TR-005 and TR-012 have `attributes["is_fired"] == "false"` |
| 7 | `test_data_objects.py` | `test_anomaly_investigation_under_tr004` | DataObject named "Anomaly Investigation — TR-004 Fuel Turbopump" has `parent_id == tr004.id` |
| 8 | `test_data_objects.py` | `test_predecessor_chain` | TR-002 has `predecessor_ids` containing TR-001's id; TR-006 has two predecessors (TR-005 and the anomaly investigation) |
| 9 | `test_timeseries.py` | `test_lumen_sensors_container_exists` | container named `"lumen-inspired-sensors"` is found via search |
| 10 | `test_timeseries.py` | `test_timeseries_channel_count` | container has ≥ 25 timeseries (one per channel in `CHANNELS` constant from seed) |
| 11 | `test_timeseries.py` | `test_timeseries_data_points_present` | `get_all_timeseries_data_points(container_id, ts_id)` for `pc_chamber` channel returns a non-empty list for TR-001 |
| 12 | `test_file_containers.py` | `test_lumen_artifacts_container_exists` | container named `"lumen-inspired-artifacts"` found; `is_public` |
| 13 | `test_file_containers.py` | `test_file_references_present` | the artifacts container has ≥ 1 file reference linked to TR-014 (qualification fire artifacts) |
| 14 | `test_annotations.py` | `test_semantic_annotations_present` | at least one of the seeded TR-00x DataObjects has a semantic annotation whose predicate IRI contains `shepard.dlr.de/ontologies/experiment` |
| 15 | `test_auth.py` | `test_unauthenticated_returns_401` | `GET /shepard/api/collections` (no auth) → 401; never 404 (proves REST class is in JAR) |
| 16 | `test_auth.py` | `test_v2_endpoints_return_401_not_404` | `GET /v2/admin/features`, `GET /v2/users/me/preferences`, `GET /v2/instance/identity` (no auth) → 401/403; never 404 |
| 17 | `test_auth.py` | `test_authenticated_collections_list` | same `GET /shepard/api/collections` with API key → 200 and `results` key present |
| 18 | `test_v2_features.py` | `test_admin_features_list` | `GET /v2/admin/features` with admin API key → 200; response is JSON array/object |
| 19 | `test_v2_features.py` | `test_container_annotations_endpoint` | `GET /v2/timeseries-containers/{lumen_sensors.id}/annotations` → 200; `annotations` key in body |
| 20 | `test_v2_features.py` | `test_chart_view_roundtrip` | `PATCH /v2/timeseries-containers/{id}/chart-view` with a JSON body → 200; subsequent `GET` returns the same body |
| 21 | `test_error_shapes.py` | `test_404_has_no_stack_trace` | `GET /shepard/api/collections/999999` (authenticated) → 404; body does not contain `"at de.dlr"` or `"Exception"` |
| 22 | `test_error_shapes.py` | `test_400_body_has_message` | `POST /shepard/api/collections` with empty JSON body → 400 or 422; body contains `"message"` or `"violations"` key |

### Implementation sketch: tests 1–3 and 5–8

```python
# e2e/api/tests/test_collections.py

import pytest
from shepard.errors import ShepardNotFound

LUMEN_NAME = "LUMEN-Inspired Hotfire Test Campaign — Q3 2024 (synthetic)"

def test_lumen_collection_exists(lumen_collection):
    assert lumen_collection.name == LUMEN_NAME
    assert "synthetic" in (lumen_collection.description or "").lower()

def test_lumen_collection_is_public(client, lumen_collection):
    perms = client.collections.get_collection_permissions(lumen_collection.id)
    assert perms.permission_type.value == "PUBLIC"

def test_crud_round_trip(temp_collection, client):
    fetched = client.collections.get_collection(temp_collection.id)
    assert fetched.name == temp_collection.name
    client.collections.delete_collection(temp_collection.id)
    with pytest.raises(ShepardNotFound):
        client.collections.get_collection(temp_collection.id)
```

```python
# e2e/api/tests/test_data_objects.py

HOLD_DAYS = {5, 12}
ANOMALY_NAME = "Anomaly Investigation — TR-004 Fuel Turbopump"

def test_fifteen_runs_exist(lumen_data_objects):
    tr_names = [n for n in lumen_data_objects if n.startswith("TR-")]
    assert len(tr_names) == 15, f"Expected 15 TR-0xx objects, found: {sorted(tr_names)}"

def test_hold_days_not_fired(lumen_data_objects):
    for n in HOLD_DAYS:
        name = f"TR-{n:03d}"
        do = lumen_data_objects[name]
        assert do.attributes["is_fired"] == "false", f"{name} should not be fired"

def test_anomaly_investigation_under_tr004(client, lumen_collection, lumen_data_objects):
    tr004 = lumen_data_objects["TR-004"]
    all_dos = client.dataobjects.get_all_data_objects(lumen_collection.id)
    anomaly = next((o for o in all_dos if o.name == ANOMALY_NAME), None)
    assert anomaly is not None, "Anomaly investigation DataObject not found"
    assert anomaly.parent_id == tr004.id

def test_predecessor_chain(lumen_data_objects):
    tr001 = lumen_data_objects["TR-001"]
    tr002 = lumen_data_objects["TR-002"]
    assert tr001.id in (tr002.predecessor_ids or [])

def test_tr006_has_two_predecessors(lumen_data_objects, client, lumen_collection):
    tr006 = lumen_data_objects["TR-006"]
    assert len(tr006.predecessor_ids or []) == 2, (
        "TR-006 should have TR-005 and anomaly investigation as predecessors"
    )
```

```python
# e2e/api/tests/test_auth.py

def test_unauthenticated_returns_401(http_anon):
    r = http_anon.get("/shepard/api/collections")
    assert r.status_code in (401, 403), (
        f"Expected 401/403, got {r.status_code}. "
        "A 404 means the REST class is missing from the JAR."
    )

def test_v2_endpoints_return_401_not_404(http_anon):
    endpoints = [
        "/v2/admin/features",
        "/v2/users/me/preferences",
        "/v2/instance/identity",
    ]
    for path in endpoints:
        r = http_anon.get(path)
        assert r.status_code in (401, 403), (
            f"{path} returned {r.status_code}, not 401/403. "
            "Check that the REST class compiled into the image."
        )

def test_authenticated_collections_list(http):
    r = http.get("/shepard/api/collections")
    assert r.status_code == 200
    body = r.json()
    assert "results" in body or isinstance(body, list), (
        "Expected paginated envelope with 'results' key or a list"
    )
```

---

## §7. Seed Dependency

### 7.1 Relationship

The integration suite is a **consumer** of `examples/lumen-showcase/seed.py`,
not a replacement. The seed must finish successfully before any test in
`test_data_objects.py`, `test_timeseries.py`, `test_file_containers.py`, or
`test_annotations.py` is meaningful.

### 7.2 Detect missing seed (fail fast)

The `lumen_collection` session fixture (§4.2) uses a search query to find the
collection by its exact canonical name. If not found, it calls `pytest.fail()`
with a human-readable message before any test runs, rather than letting
individual tests fail with cryptic `AttributeError: 'NoneType' object has no
attribute 'id'` errors.

A companion marker `@pytest.mark.seed_required` should decorate all tests that
need seeded data, enabling the CI step to run `test_auth.py` and
`test_error_shapes.py` even on a freshly deployed stack before seeding:

```bash
# Run auth + error-shape tests without seed:
pytest e2e/api/tests/test_auth.py e2e/api/tests/test_error_shapes.py -v

# Run full suite (requires seed to have completed):
pytest e2e/api/ -v
```

### 7.3 Idempotency

`seed.py` is already idempotent (by-name lookup before create; `--reset` to
wipe). The integration suite must also be idempotent:

- Tests that create data must clean up in a `finally` block or via a
  function-scoped fixture's teardown.
- Tests must not hardcode integer IDs (they change across resets); look up by
  name at session start via the `lumen_collection` / `lumen_data_objects` fixtures.
- If a test leaves behind `__integration-test-temp__` entities (e.g. CI killed
  mid-run), re-running the suite will encounter a name conflict on create. The
  `temp_collection` fixture should handle this with a try-delete-then-create
  pattern or a unique suffix (`uuid.uuid4().hex[:8]`).

### 7.4 Home-showcase seed

The smoke test also checks the `home-showcase` seeder
(`infrastructure-home-showcase-seeder-1`). A minimal `test_home_showcase.py`
can verify that at least one collection seeded by `examples/home-showcase/seed.py`
exists by name, following the same `lumen_collection` fixture pattern.
This is deferred to a follow-up; document it in `aidocs/44`.

---

## §8. CI Integration

### 8.1 Why CI cannot `needs: build-backend`

The CI pipeline (`ci.yml`) builds and pushes images to GHCR; the actual deploy
happens on the host server (via `docker compose up` triggered by an operator or
Watchtower). GitHub Actions cannot observe the deploy directly. Two CI hooks are
therefore recommended:

**Hook A — Scheduled drift detection (primary post-merge check)**
A new workflow file `.github/workflows/integration-tests.yml` runs on a cron
schedule (e.g. daily at 02:00 UTC) and on `workflow_dispatch`. It hits the
production URL directly.

**Hook B — Host-side post-deploy step (catches the deploy regression immediately)**
An addition to the local deploy runbook (or an operator-triggered shell
snippet) that runs the suite immediately after `docker compose up -d` and
`./smoke-test.sh`. No code changes to CI; pure ops practice.

### 8.2 Workflow file: `.github/workflows/integration-tests.yml`

```yaml
name: API Integration Tests

on:
  schedule:
    - cron: "0 2 * * *"          # Daily drift detection
  workflow_dispatch:
    inputs:
      backend_url:
        description: "Backend base URL (e.g. https://shepard.box)"
        required: false
        default: ""

permissions:
  contents: read

jobs:
  api-integration:
    name: API integration tests
    runs-on: ubuntu-latest
    # Only run on the main branch for scheduled runs; workflow_dispatch can run anywhere.
    if: github.ref == 'refs/heads/main' || github.event_name == 'workflow_dispatch'
    steps:
      - uses: actions/checkout@v4

      - name: Set up Python 3.12
        uses: actions/setup-python@v5
        with:
          python-version: "3.12"

      - name: Install integration test dependencies
        working-directory: e2e/api
        run: |
          pip install --quiet --extra-index-url \
            https://gitlab.com/api/v4/projects/59082852/packages/pypi/simple \
            ".[test]"

      - name: Run integration tests
        working-directory: e2e/api
        env:
          BACKEND_URL: ${{ inputs.backend_url || vars.SHEPARD_BACKEND_URL || 'https://shepard.box/shepard/api' }}
          SHEPARD_API_KEY: ${{ secrets.SHEPARD_IT_API_KEY }}
          KC_URL: ${{ vars.KC_URL || 'https://shepard.box/auth' }}
          KC_REALM: ${{ vars.KC_REALM || 'shepard-demo' }}
          KC_CLIENT_ID: ${{ vars.KC_CLIENT_ID || 'frontend-dev' }}
          KC_ADMIN_USER: ${{ secrets.KC_ADMIN_USER }}
          KC_ADMIN_PASS: ${{ secrets.KC_ADMIN_PASS }}
        run: |
          pytest tests/ -v \
            --tb=short \
            --junit-xml=../../test-results/integration.xml \
            -x          # stop on first failure

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: integration-test-results
          path: test-results/integration.xml
```

**Required GitHub Actions secrets / variables:**

| Name | Type | Description |
|---|---|---|
| `SHEPARD_IT_API_KEY` | Secret | Long-lived API key on the target instance (preferred) |
| `KC_ADMIN_USER` | Secret | Keycloak admin username (fallback if no API key) |
| `KC_ADMIN_PASS` | Secret | Keycloak admin password |
| `SHEPARD_BACKEND_URL` | Variable | Default backend base URL for scheduled runs |
| `KC_URL` | Variable | Keycloak base URL |
| `KC_REALM` | Variable | Keycloak realm name |
| `KC_CLIENT_ID` | Variable | Keycloak client ID for ROPC |

### 8.3 Failure surfacing

- `pytest --junit-xml` produces JUnit XML consumed by GitHub's test-results
  viewer (no extra action needed; GitHub natively renders it in the Actions UI).
- `-x` (fail-fast) keeps the log readable: the first broken assertion is shown
  in full, not buried under 20 subsequent cascade failures.
- The workflow has `if: always()` on the upload step so artifacts are preserved
  even on failure.
- On a scheduled run, workflow failure triggers the repository owner's default
  GitHub email notification (no extra configuration required).

### 8.4 Post-deploy host-side hook (Hook B)

Add to the local deploy runbook / Makefile:

```bash
# After: docker compose -f docker-compose.yml -f docker-compose.override.yml up -d
# After: ./smoke-test.sh

deploy-test:
    @echo "Running API integration tests against local stack..."
    cd e2e/api && \
      BACKEND_URL=http://localhost:8080/shepard/api \
      KC_URL=http://localhost:8082 \
      pytest tests/ -v --tb=short
```

---

## §9. `pyproject.toml` for the suite

```toml
# e2e/api/pyproject.toml

[build-system]
requires = ["setuptools>=68"]
build-backend = "setuptools.backends.legacy:build"

[project]
name = "shepard-api-integration-tests"
version = "0.1.0"
requires-python = ">=3.11"
dependencies = [
    "httpx>=0.27",
    "python-dotenv>=1.0",
]

[project.optional-dependencies]
test = [
    "pytest>=8.0",
    "pytest-httpx>=0.30",   # optional; useful for mocking in unit tests of helpers
    # shepard-client comes from the GitLab PyPI index; install separately:
    # pip install --extra-index-url https://... shepard-client shepard
]

[tool.pytest.ini_options]
testpaths = ["tests"]
addopts = ["-ra", "--tb=short"]
markers = [
    "seed_required: test requires seed.py to have completed successfully",
]
```

---

## §10. Rollout Checklist

An implementer working from this design should complete the following in order:

1. **Create `e2e/api/` directory** with `pyproject.toml`, `conftest.py`,
   `helpers/auth.py`, and the `tests/` stubs.
2. **Wire local dev smoke** — add `make integration-test` target that hits
   `localhost:8080`.
3. **Verify against running stack** — run `pytest tests/test_auth.py -v` (no
   seed required) against the dev instance. Fix any import issues.
4. **Add seed-dependent tests** — implement `test_collections.py` and
   `test_data_objects.py` from the table in §6; run against a fully seeded dev
   stack.
5. **Create GitHub Actions workflow** — add `.github/workflows/integration-tests.yml`
   per §8.2. Set repository secrets. Do a manual `workflow_dispatch` run against
   the production instance.
6. **Update trackers** — update `aidocs/34-upstream-upgrade-path.md` (no new
   API surface; this is test infra, entry is "infra only") and
   `aidocs/44-fork-vs-upstream-feature-matrix.md` (new row: "API integration
   test suite", shipped/designed).

---

## §11. Known Limitations and Follow-ons

| Item | Notes |
|---|---|
| No structured-data assertions | `lumen-inspired-runlogs` container is seeded but no test cases cover it in v1. Add `test_structured_data.py` once the runlog seed is stable. |
| No semantic-repository assertions | Ontology pre-seed (`N1c2`) is exercised only indirectly via annotation IRIs. A dedicated `test_ontologies.py` should verify `GET /v2/admin/semantic/ontologies` lists the seeded ontologies. |
| `home-showcase` coverage | Deferred to follow-up; tracked in `aidocs/44`. |
| Keycloak ROPC deprecation | RFC 9700 discourages ROPC for public clients. If `frontend-dev` client becomes confidential or ROPC is disabled, switch to client-credentials grant (add a dedicated `shepard-integration-tests` service client in the Keycloak realm). The `SHEPARD_API_KEY` path in the `api_key` fixture bypasses this entirely and should be the preferred CI path. |
| TLS verification | Dev stack uses Caddy with a self-signed cert; `verify=False` is acceptable for a dev integration suite. Production CI should supply a CA bundle via `httpx.Client(verify="/path/to/ca.pem")` if the cert is not publicly trusted. |
| Test isolation for timeseries data | Timeseries data-point tests read seeded data; they must not write. Separate writing from reading with a `@pytest.mark.readonly` marker and skip writes if running against production. |
