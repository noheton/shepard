# Convenience Clients — `shepard-py` and `shepard-ts` (P16)

**Snapshot date.** 2026-05-05.

**Scope.** Focused design note for the proposed convenience layers
that wrap the OpenAPI-generated `shepard_client` (Python) and
`@dlr-shepard/shepard-client` (TypeScript). Backlog item **P16**
(see `aidocs/16-dispatcher-backlog.md`). Surfaced by §5.6 of
`aidocs/platform/23-api-critique.md` after §2.10 catalogued the 14-line
prelude every Python user pays before a single domain call.

This document does **not** propose replacing the generated clients,
introducing a new RPC framework, or adopting tRPC — that path was
already evaluated and rejected in `aidocs/23 §5`. The wrapper sits
strictly **on top** of the generator's output and only calls into
generated symbols.

**Companion docs.** `aidocs/16-dispatcher-backlog.md` (P10 SQL
timeseries, P12 S3 presigned, P17 generator pin / Kiota PoC, P17b
`@Schema(name=…)` CI lint, P21 PATCH adoption, L5 API-key
`validUntil`, E7 HDF5), `aidocs/platform/23-api-critique.md` §5.6,
`aidocs/input/input_raw.md:1-30` (the canonical 14-line example).

**Status legend.** Same as `13-search-improvements.md`:
🟥 HIGH / 🟧 MED / 🟨 LOW / 🟪 ARCH / ✅ DONE.

---

## 1. Goals and non-goals

**Goals.**

- Collapse the 14-line Python prelude (`input_raw.md:1-30`) to
  three lines: `Client(...)` + create + get.
- Expose **domain-shaped** attributes (`sh.collections`,
  `sh.timeseries`, `sh.search`, …) that mirror the generated `*Api`
  classes one-to-one, with no method renames.
- Ship pagination iterators that hide `?page&size` plumbing.
- Ship three workflow helpers — `to_pandas`, `to_excel`,
  `ro_crate` — for the operations users currently re-implement.
- Keep the wrapper dependency-free at the **core** layer; gate
  workflow helpers behind PEP 508 extras.

**Non-goals.**

- No replacement of the generated client. Both ship side-by-side.
- No new auth flows beyond what shepard already speaks (API key
  today; bearer JWT if and when the backend exposes it).
- No offline cache, sync helpers, or mutation queues.
- No ORM-style lazy attribute traversal (`collection.dataobjects[0]`
  silently fetching). The wrapper is thin.
- No Async-first redesign in v0.1; see open question §11.

---

## 2. The 14-line vs 3-line worked example

**Before** — verbatim from `aidocs/input/input_raw.md:7-34`:

```python
from shepard_client.api_client import ApiClient
from shepard_client.configuration import Configuration

HOST = "https://backend.shepard.example.com/shepard/api"
APIKEY = "Your api key"

conf = Configuration(host=HOST, api_key={"apikey": APIKEY})
client = ApiClient(configuration=conf)

from shepard_client.api.collection_api import CollectionApi
from shepard_client.models.collection import Collection

collection_api = CollectionApi(client)
collection_to_create = Collection(
    name="MyFirstCollection",
    description="...",
    attributes={"a": "1", "b": "2"},
)
created = collection_api.create_collection(collection=collection_to_create)
```

Fourteen non-blank lines, four imports, two layers of indirection
(`Configuration` → `ApiClient` → `CollectionApi`).

**After** — the proposed `shepard` wrapper:

```python
import shepard

sh = shepard.Client(host="https://backend.shepard.example.com/shepard/api", apikey="…")
created = sh.collections.create(name="MyFirstCollection", description="…", attributes={"a": "1"})
```

Three lines (one import, one client, one call). The `Collection`
model can stay implicit because `create` accepts kwargs and
constructs the IO object internally; users who need typed models
still get `from shepard.models import Collection` re-exported from
the generated package.

---

## 3. The Python API shape

### 3.1 `Client` constructor

Pulls all knobs from the existing `Configuration` class shipped by
the generator (host, api_key dict, ssl_ca_cert, verify_ssl, etc.).

```python
class Client:
    def __init__(
        self,
        host: str,
        apikey: str | None = None,
        bearer_token: str | None = None,   # forward-compat for L5 / future JWT
        timeout: float | None = None,
        verify_ssl: bool = True,
        ssl_ca_cert: str | None = None,
    ) -> None:
        cfg = shepard_client.Configuration(host=host)
        if apikey:
            cfg.api_key = {"apikey": apikey}
        if bearer_token:
            cfg.access_token = bearer_token
        cfg.verify_ssl = verify_ssl
        cfg.ssl_ca_cert = ssl_ca_cert
        self._api = shepard_client.ApiClient(configuration=cfg)
        if timeout is not None:
            self._api.rest_client.pool_manager.connection_pool_kw["timeout"] = timeout
```

The generated `Configuration` is documented to support both API
key (today) and `access_token` (bearer); the L5 `validUntil` field
is a property of the *server-side* API key — see §9 risks for the
client-side warning.

### 3.2 Domain-shaped attributes

The OpenAPI tags in the backend are the contract for the generated
`*Api` class names. From `Constants.java` and the `@Tag(name=…)`
declarations across the 29 `*Rest.java` classes, the canonical set
is:

| Wrapper attribute | Generated `*Api` | Backend `@Path` | Notes |
|---|---|---|---|
| `sh.collections` | `CollectionApi` | `/collections` | `CollectionRest.java:44` |
| `sh.dataobjects` | `DataObjectApi` | `/collections/{id}/dataObjects` | `DataObjectRest.java` |
| `sh.timeseries` | `TimeseriesContainerApi` | `/timeseriesContainers` | `TimeseriesRest.java:65` (read), plus `to_pandas` etc. |
| `sh.files` | `FileContainerApi` | `/fileContainers` | `FileRest.java` |
| `sh.structured_data` | `StructuredDataContainerApi` | `/structuredDataContainers` | |
| `sh.spatial_data` | `SpatialDataContainerApi` | `/spatialDataContainers` | |
| `sh.search` | `SearchApi` | `/search` | `SearchRest.java` (see `13-search-improvements.md` for unification proposal) |
| `sh.permissions` | (cross-cuts each Api) | various | Shortcut: `sh.permissions.for_collection(id)` returns `CollectionApi.get_permissions` |
| `sh.api_keys` | `ApikeyApi` | `/apikeys` | `ApiKeyRest.java`; warns on near-expiry per L5 |
| `sh.export` | (cross-cuts) | `/collections/{id}/export` | `CollectionRest.java:257-278`; uses `ExportConstants.ROCRATE_METADATA` |
| `sh.subscriptions` | `SubscriptionApi` | `/subscriptions` | `SubscriptionRest.java` |
| `sh.semantic` | `SemanticAnnotationApi`, `SemanticRepositoryApi` | `/semantic*` | Convenience: groups the four annotation Apis |
| `sh.references` | `BasicReferenceApi`, `TimeseriesReferenceApi`, … | `/*References` | Grouped accessor, otherwise full list bloats tab-complete |
| `sh.users` / `sh.usergroups` | `UserApi`, `UsergroupApi` | `/users`, `/userGroups` | |
| `sh.versionz` | `VersionzApi` | `/versionz` | `VersionzRest.java` |

**Rule:** the wrapper attribute name is the snake-case lower-plural
of the OpenAPI tag (`COLLECTION` → `collections`); the generated
`*Api` is wrapped, never re-implemented. Method names on the proxy
objects match the generated method names exactly
(`create_collection`, `get_all_collections`, `get_collection_by_id`,
…) so `dir(sh.collections)` is identical to the generated class.

### 3.3 Pagination iterator

The backend returns **flat arrays** for paginated list endpoints
(see `CollectionRest.java:65,77-78` — `@Schema(type = ARRAY,
implementation = CollectionIO.class)`, with `?page&size` as query
params). There is **no `Page<T>` envelope**, no `total`, no
`content`. This matters for the iterator design: termination is by
"got fewer than `size` items" or "got an empty page".

```python
def iter_pages(fetch, page_size: int = 100):
    page = 0
    while True:
        items = fetch(page=page, size=page_size)
        if not items:
            return
        for item in items:
            yield item
        if len(items) < page_size:
            return
        page += 1
```

Exposed on each domain proxy as two methods:

- `sh.collections.iter(size=100, **filters)` — generator,
  transparent next-page fetch. Name chosen to make the cost
  visible (vs a plain `.all()`).
- `sh.collections.list(size=100, **filters)` — eager: returns
  `list[Collection]`. Refuses (raises) above a configurable cap
  (`Client(..., list_cap=10_000)`).

If pagination shape ever moves to a `Page<T>` envelope (see
`aidocs/16` L6 / `aidocs/18`), the iterator gains a `total`-aware
fast path; the public signature does not change.

### 3.4 Error model

Map the generated `ApiException` (which carries `.status` and
`.body`) into a thin hierarchy keyed by HTTP status:

```python
class ShepardError(Exception):
    def __init__(self, status: int, body: str, headers: dict): ...

class ShepardBadRequest(ShepardError):    pass   # 400
class ShepardUnauthorized(ShepardError):  pass   # 401
class ShepardForbidden(ShepardError):     pass   # 403
class ShepardNotFound(ShepardError):      pass   # 404
class ShepardConflict(ShepardError):      pass   # 409
class ShepardValidation(ShepardError):    pass   # 422
class ShepardServerError(ShepardError):   pass   # 5xx
```

The proxy methods catch `ApiException`, look up the subclass by
status, and re-raise. No exception is swallowed. This is the
single user-visible surface change beyond the constructor — and
it lets people write `except ShepardNotFound:` without inspecting
`exc.status == 404`.

### 3.5 The three workflow helpers

```python
def to_pandas(
    self,
    timeseries_id: int,
    *,
    container_id: int | None = None,
    start: datetime | None = None,
    end: datetime | None = None,
    fields: list[str] | None = None,
    chunksize: int | None = None,
) -> "pandas.DataFrame": ...

def to_excel(
    self,
    timeseries_id: int,
    path: str | Path,
    *,
    container_id: int | None = None,
    start: datetime | None = None,
    end: datetime | None = None,
    fields: list[str] | None = None,
) -> Path: ...

def ro_crate(
    self,
    collection_id: int,
    path: str | Path,
) -> Path: ...
```

- **`to_pandas`** (pre-P10 shape): paginates the existing
  `GET /timeseriesContainers/{cid}/timeseries/{tid}/payload`
  endpoint (see `TimeseriesRest.java`), assembles a `DataFrame`
  by `pd.concat` with `chunksize` to bound memory.
- **`to_pandas`** (post-P10 shape): issues a single
  `POST /sql/timeseries` (see `aidocs/16` P10) and decodes one
  CSV/Arrow stream. Helper signature does not change; internal
  branch on `Client.api_capabilities` (a small probe against
  `/versionz`).
- **`to_excel`** = `to_pandas` then `df.to_excel(path,
  sheet_name=field)` per field, using `openpyxl`.
- **`ro_crate`** issues `GET /collections/{id}/export` (see
  `CollectionRest.java:257-278`), streams the response body
  (which contains `ro-crate-metadata.json` per
  `ExportConstants.java` and `ExportBuilder.java`) to disk. No
  parsing, no buffering — `urllib3` `preload_content=False` +
  `shutil.copyfileobj`.

---

## 4. The "no new dependencies" claim — interrogated

The generated `shepard_client` already pulls in `urllib3`,
`pydantic >= 2`, `python-dateutil`, and `typing-extensions` (the
standard `openapi-generator-cli:v7.12.0` python output, observed in
`.gitlab/ci/clients/python.gitlab-ci.yml`). The wrapper's **core**
adds nothing: `Client`, domain proxies, pagination, and the error
hierarchy use only stdlib + the generated client.

The workflow helpers do need something:

| Helper | Required pkg | Status |
|---|---|---|
| `to_pandas` | `pandas` | optional extra |
| `to_excel` | `pandas` + `openpyxl` | optional extra |
| `ro_crate` | (none) | always available |

Hard-depending on `pandas` would balloon the install for the 80%
of users who only do CRUD. Use PEP 508 extras:

```toml
# clients/python/shepard/pyproject.toml
[project]
name = "shepard"
dependencies = ["shepard-client>=5.2,<6"]

[project.optional-dependencies]
pandas = ["pandas>=2.0"]
excel  = ["pandas>=2.0", "openpyxl>=3.1"]
all    = ["pandas>=2.0", "openpyxl>=3.1"]
```

`pip install shepard` → core only. `pip install shepard[excel]` →
adds `to_excel`. The helpers themselves gate at call-site:

```python
def to_pandas(self, *args, **kw):
    try:
        import pandas as pd
    except ImportError as e:
        raise ImportError(
            "to_pandas requires `shepard[pandas]`. "
            "Install with: pip install 'shepard[pandas]'"
        ) from e
    ...
```

**Verdict.** "No new dependencies" is **half-true**: true for
core, true-with-extras for helpers. The phrasing in the brief
should be tightened to "no new required dependencies".

---

## 5. The 150-LoC budget — interrogated

Estimated breakdown for the **core** package (excluding helpers):

| Component | LoC |
|---|---|
| `Client.__init__` + auth + timeout wiring | ~30 |
| Domain-attribute proxies (lazy import + factory) | ~30 |
| Pagination iterator + `iter` / `list` wrappers | ~25 |
| Error mapping (`ShepardError` hierarchy + dispatch) | ~20 |
| Three workflow helpers (signatures + body) | ~30 |
| Trailing utilities (logging, near-expiry warning) | ~15 |
| **Total core** | **≈150** |

Plus:

- **Tests.** ~200 LoC. Unit tests for the proxy factory and
  error mapping; one or two round-trip tests with
  `pytest-httpserver` (already used elsewhere; if not, the
  alternative is `responses` or `requests-mock` — the existing
  `clients/tests/python/` only does class-shape diffing
  (`main.py:1-258`) so any choice is greenfield).
- **Docs.** ~30 LoC `README.md` with the 3-line hello world plus
  one example per workflow helper.

**Verdict.** **Achievable.** 150 LoC is a useful constraint and
roughly matches the breakdown above. Tests will roughly equal it.
Total package weight stays under ~400 LoC excluding generated
material. The risk is scope creep on the workflow helpers —
guard with a hard "if it does not fit in 30 LoC, push it to a
post-P10 follow-up" rule.

---

## 6. Where it lives and how it's published

**Location.** `clients/python/shepard/` — sibling to the
generated `clients/python/shepard_client/` (the latter is what
the generator overwrites every CI run; see
`.gitlab/ci/clients/python.gitlab-ci.yml:6-12`). The generator
overwrites only its own subtree; the wrapper subtree is hand-
maintained.

**PyPI names.**

- `shepard-client` — generated, stays as-is. Today indexed at
  `https://gitlab.com/api/v4/projects/59082852/packages/pypi/simple`
  (see `clients/tests/python/requirements.txt:1-2`).
- `shepard` — new umbrella, depends on
  `shepard-client>=5.2,<6` as its only required runtime dep.

Same PyPI index as the generator (GitLab Maven Packages registry
on the same project). Open question §11 covers whether to
register on public PyPI as well.

**Versioning.** `shepard` tracks `shepard-client`'s minor
version. A backend MR that produces `shepard-client==5.3.0`
triggers a `shepard==5.3.x` release on the same minor. Patch
increments are wrapper-only fixes. The `>=X,<Y` pin protects
against generator regenerations that flip class names mid-
release.

**CI.** Add a job under `.gitlab/ci/clients/python.gitlab-ci.yml`
that runs after `build_python_client`:

```yaml
.test_python_wrapper:
  stage: test
  image: python:3.13.1-alpine3.19
  needs: [build_python_client]
  script:
    - cd clients/python/shepard
    - pip install -e ../[shepard_client_local]  # local generated artefact
    - pip install -e .[all]
    - pip install pytest pytest-httpserver
    - pytest -q
```

The matching upload job is a clone of `.upload_python_client`
pointed at the wrapper subtree.

---

## 7. Maintenance story

The wrapper drifts when the API drifts. Three mitigations:

1. **CI lint (P17b).** Every IO class on the backend carries
   `@Schema(name=…)`. Backlog item P17b makes the absence of this
   annotation a CI failure. With names pinned, generator output
   is stable across regenerations: the wrapper's hard-coded
   import statements (`from shepard_client.api.collection_api
   import CollectionApi`) keep resolving. A name flip blows up
   the wrapper's import-time check at CI time, not at user
   `pip install` time.
2. **Wrapper rule.** Convenience methods may only call
   generated endpoints. **No direct HTTP**, no extra business
   logic. Concretely: every wrapper method body either
   (a) calls into a `_generated.*Api.method(...)` symbol, or
   (b) does pagination / dataframe assembly / error mapping
   around such a call. A 6-line ruleset goes in the wrapper's
   `CONTRIBUTING.md`. Lint enforcement is out of scope; code
   review carries it.
3. **Coverage check.** At import time, the wrapper iterates
   `dir(shepard_client.api)` and checks that every `*Api` is
   either explicitly wrapped or explicitly listed in
   `_INTENTIONALLY_UNWRAPPED`. A new generated `*Api` (e.g. when
   a new resource ships in the backend) logs `UserWarning("new
   API class FooApi is not wrapped — accessible as
   sh.raw.FooApi")`. ~10 LoC.

When P4 (`/v1/` versioning) lands, the wrapper bumps a major
version; the previous major continues to track `/v1/` while the
new major targets `/v2/`. The `quarkus-openapi-generator`
configuration would need a per-version target — the generator
already supports `-o` per profile, so two CI jobs and two PyPI
artefacts is mechanical.

---

## 8. The TypeScript counterpart

Same shape (`Client`, domain attributes, pagination, error
hierarchy, the workflow helpers that translate). Three real
differences:

### 8.1 Browser vs Node

The generated TS client uses `typescript-fetch` (see
`clients/tests/typescript/package.json:7-9`: the generator command
sets `-g typescript-fetch`). Bare `fetch` is isomorphic on Node
≥ 18 and all current browsers, so the **core** of `shepard-ts`
is isomorphic with no extra effort.

Helpers that need Node-only APIs (file streaming with `fs`) live
under a subpath import:

```typescript
import { Client } from "shepard";              // browser-safe
import { downloadRoCrate } from "shepard/node"; // Node-only, uses fs
```

### 8.2 `to_pandas` does not apply; closest equivalents

```typescript
// Whole-payload to JS objects (requires the user to materialise it)
sh.timeseries.toRecords(timeseriesId, { start, end, fields }): Promise<Record<string, unknown>[]>;

// Streaming variant for Node
sh.timeseries.toStream(timeseriesId, { start, end, fields }): AsyncIterable<Record<string, unknown>>;

// RO-Crate download — Blob in browser, ReadableStream in Node
sh.export.roCrate(collectionId): Promise<Blob>;          // browser
import { downloadRoCrate } from "shepard/node";
await downloadRoCrate(sh, collectionId, "/tmp/x.zip");   // Node
```

For Excel, recommend deferring to user code (`xlsx` is heavy,
licensed under SheetJS terms). Document an example using
`exceljs` if a real demand surfaces.

### 8.3 Bundle size — tree-shaking discipline

Per-domain entry points so an app that only uses Collections
does not pay for Search:

```typescript
import { Client } from "shepard";
import { Collections } from "shepard/collections";

const sh = new Client({ host, apikey });
const c = await Collections(sh).create({ name: "..." });
```

The default `import { Client } from "shepard"` re-exports a
proxy object whose getters lazy-import the `*Api` modules. With
ES modules + `sideEffects: false` in `package.json`, bundlers
(Vite, Rollup, esbuild) drop the unused domains.

### 8.4 Distribution

- `@dlr-shepard/shepard-client` — generated, today (see
  `clients/tests/typescript/package.json:8`).
- `@dlr-shepard/shepard` — new umbrella, depends on
  `^5.1.2` of the generated package (or whatever the latest
  shipped tag resolves to).

Same registry as the generated client (the GitLab npm registry
URL embedded in the TS client generator command). Open question
§11 covers public npm.

---

## 9. Risks

🟧 **Pagination foot-guns.** A naive `for c in sh.collections.iter():
…` silently fans out to N HTTP calls. Mitigations: name the lazy
method `iter()` (not `all()`), document the cost in the
docstring, add a `max_pages` safety cap with a warning at the
default (e.g. 100 pages = 10 000 items at `size=100`).

🟥 **`to_pandas` memory.** A multi-million-row timeseries OOMs
the user's process. Mitigations: `chunksize` parameter exposed
explicitly; once P10 ships, add a sibling `to_arrow()` returning
a streaming `pyarrow.RecordBatchReader`; document the
"refuse-without-time-bounds" mode (open question §11) as a
configurable safety.

🟧 **Generator regenerations.** The import-time coverage check
from §7 catches name flips, but a flip mid-release-cycle still
breaks tagged users. Mitigations: tighten `shepard-client>=X,<Y`
range to a single minor; bump `shepard` whenever
`shepard-client` bumps; treat coverage warnings as soft signals,
not exceptions, so a transient new `*Api` does not break
existing user code.

🟨 **API-key expiry surprise.** L5 introduces a `validUntil`
field on API keys. The wrapper queries `GET /apikeys/me` (or
nearest) at first use and emits `UserWarning` if `validUntil`
is within 7 days. ~5 LoC, deletable, no behaviour change. If
the endpoint isn't reachable (older backend), silently skip.

🟨 **Auth not yet bearer-token.** The generated `Configuration`
exposes both `api_key` and `access_token`; the latter is unused
on the backend today (only API key flows are wired). Forward-
compatible parameter; warn if both supplied.

🟨 **Docs lag.** The wrapper's README is the developer-facing
front door. Treat README drift the same as code drift — every
new domain Api ships with a one-line example.

---

## 10. Sized rollout

| Phase | Size | Scope | Artefact |
|---|---|---|---|
| **1** | S, ~1 wk | Core: `Client`, domain proxies (all `*Api`s), pagination, error mapping. **No** workflow helpers. | `shepard==0.1.0` (Python only) |
| **2** | S, ~1 wk | The three workflow helpers gated behind extras: `pandas`, `excel`. `ro_crate` ships in core (no extra). | `shepard==0.2.0` |
| **3** | S, ~3 d | TypeScript counterpart: mirror Phase 1 + `roCrate` + `toRecords` + `toStream`. No Excel helper. | `@dlr-shepard/shepard@0.1.0` |
| **4** | XS, follow-up | Re-target `to_pandas` to P10 `POST /sql/timeseries`; add `to_arrow()` companion. Non-breaking — same signature. | `shepard==0.3.0` |

Each phase is independently mergeable. Phase 4 is gated on the
P10 backlog item landing.

---

## 11. Open questions for the maintainer

1. **One umbrella or two packages?** Should the wrapper claim
   the short PyPI / npm name (`shepard`) and rename the generated
   client (`shepard-client` stays), or ship as
   `shepard-py-helpers` and leave `shepard-client` as the user-
   facing entry point? The brief assumes the former.
2. **`to_pandas` default time window.** "All data" (potentially
   huge) vs refuse-without-explicit-bounds. Suggested default:
   refuse if the server-side metadata reports > N data points
   (configurable, default 10 M); otherwise return all.
3. **Sync-only or async-first?** The generated Python client is
   sync (`urllib3`-based). Building an `httpx`-style async layer
   in v0.1 doubles surface area. Recommend sync-first, evaluate
   async after Phase 3. TS is async-first by default.
4. **Public-PyPI / public-npm distribution?** Today both
   generated clients ship to GitLab Maven/PyPI/npm registries
   only. Publishing the wrapper publicly raises the
   discoverability bar but commits to a stable name; tying it
   to the generator's existing release cadence is safer.
5. **Cadence for tracking new generator output.** Every backend
   MR (auto), every release tag, or weekly batch? Auto-on-MR
   maximises freshness but multiplies CI cost; per-release is
   the proven cadence the generated client itself uses.
6. **Wrapper's coverage policy.** Hard fail (CI breaks if a new
   `*Api` is not wrapped) vs soft warn (logs but ships). The
   brief assumes soft warn; hard fail is also defensible.

---

## 12. Cross-references

- `aidocs/16-dispatcher-backlog.md` — P16 (this), P10 (SQL
  timeseries), P12 (S3 presigned), P17 / P17b (generator pin /
  CI lint), P21 (PATCH), L5 (`validUntil`), E7 (HDF5).
- `aidocs/platform/23-api-critique.md` §5.6 (motivation) and §2.10
  (boilerplate cost).
- `aidocs/semantics/13-search-improvements.md` — once unified search
  ships, `sh.search` collapses to a single method.
- `aidocs/input/input_raw.md:1-30` — the verbatim 14-line
  example.
- Backend code refs cited inline:
  `CollectionRest.java:44,65,77-78,257-278`,
  `TimeseriesRest.java:65`, `ExportConstants.java`,
  `ExportBuilder.java`, `Constants.java` (tag names),
  `.gitlab/ci/clients/python.gitlab-ci.yml:6-12`,
  `clients/tests/python/{requirements.txt,src/main.py}`,
  `clients/tests/typescript/package.json:7-9`.
