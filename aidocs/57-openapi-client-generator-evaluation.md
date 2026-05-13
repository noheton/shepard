# OpenAPI Client Generator Evaluation — `/v2/` Codegen

**Status.** Concept design.
**Snapshot date.** 2026-05-12.
**Audience.** Contributors evaluating the v2 client-codegen path.
**Originating items.** User request: "look at alternative client
generators like Microsoft Kiota for the v2. benefits, disadvantages,
only open-source, no commercial vendors. commercial go in a separate
section, with short description, and if interesting to look at."
Couples to `aidocs/16` row **P17** (queued — "pin
`openapi-generator-cli` version across languages, add Microsoft
Kiota PoC"), `aidocs/23 §5` (the original critique that recommended
"stay with OpenAPI Generator + prototype Kiota in parallel"),
`aidocs/28` (the integrated paradigms-and-clients synthesis),
`aidocs/27` (the convenience-wrapper layer that rides on top of
generated clients), `aidocs/47 §4.5` (DX5 — OpenAPI hot-reload as
part of dev experience), `aidocs/56` (sibling agent's MCP-tool-
naming work introducing `x-mcp-*` OpenAPI extensions), and the
**`CLAUDE.md` API-version policy** (`/v2/` is the development
surface; the codegen choice for `/v2/` is independent from the
upstream-frozen `/shepard/api/...` client story).

---

## 1. Why we're looking

The current pipeline (`aidocs/23 §5.1`) is **OpenAPI Generator CLI**
pinned to two different versions across languages:

| Client | Generator | Image | Post-processing |
|---|---|---|---|
| Python | `openapitools/openapi-generator-cli:v7.12.0` (`python`) | `dlr-shepard-clients/python` | `scripts/.../patch_openapi_for_python.py` strips `required` on read-only/nullable fields |
| Java | `openapitools/openapi-generator-cli:v7.16.0` (`java`) | `dlr-shepard-clients/java` | none |
| TypeScript | `openapitools/openapi-generator-cli:v7.12.0` (`typescript-fetch`) | `dlr-shepard-clients/typescript` | none |

References: `.gitlab/ci/clients/{python,java,typescript}.gitlab-ci.yml`,
`.gitlab/ci/jobs/unrequire-attributes-for-python.yml`,
`clients/java/config.yaml`, `openapitools.json`. The generated
clients ship from the upstream `dlr-shepard-clients/*` repos to
PyPI / npm / Maven Central.

Three pressures push us to re-evaluate **now**:

1. **L2d cutover** — the `/v2/` shelf becomes the native surface
   (`CLAUDE.md` API-version policy + `aidocs/25` L2 chain). A
   parallel client crank for `/v2/` is the natural moment to
   re-pick the generator; the upstream-compat clients for
   `/shepard/api/...` keep their existing pipeline regardless.
2. **OpenAPI 3.1 ahead** — `aidocs/23 §4.10` (P15) and the
   sibling MCP-tools doc (`aidocs/56`) both assume `/v2/` ships
   3.1 once `smallrye-open-api 4.x` is stable. The
   `patch_openapi_for_python.py` band-aid exists *because* of
   3.0's `nullable` ambiguity; 3.1 closes that. Generators differ
   sharply on 3.1 readiness.
3. **`x-mcp-*` extensions** — `aidocs/56` introduces vendor
   extensions on shepard handlers to drive MCP-tool naming /
   grouping. A generator that silently strips `x-*` blocks that
   work cold.

The user named **Kiota** specifically. This doc evaluates it
against the OSS field and recommends.

---

## 2. What "good" looks like for shepard

Eight criteria, ranked:

1. **Open-source primary** (per user request) — commercial only as
   a tour in §5. Apache-2.0 / MIT / BSD only; copyleft-licensed
   generators are non-starters per the licence policy in
   `CLAUDE.md` security gates.
2. **Multi-language** — Java + Python + TypeScript are the shipped
   trio (`aidocs/40 §1`); .NET / Go / Rust are stretch (an
   engineering-physics community ask, see §8).
3. **Idiomatic output** — the SDK should feel like a hand-written
   one for that language. Fluent path-builders (`client.collections
   .byAppId(id).get()`) are excellent for shepard's hierarchical
   resource graph; operation-per-method (`CollectionApi.getById(id)`)
   is acceptable; Go-flavoured Java is the bug.
4. **OpenAPI 3.1 first-class** — JSON Schema 2020-12, native
   `nullable` via `type: [..., "null"]`, no post-processing
   monkey-patch. shepard targets 3.1 once `smallrye-open-api 4.x`
   lands (P15).
5. **Maintenance health** — releases in the last 6 months (so:
   anything dated newer than ~Nov 2025 as of this snapshot),
   responsive issue tracker, multiple maintainers.
6. **Plays nicely with `x-mcp-*` extensions** — at minimum doesn't
   strip them; better if the codegen surfaces them in the
   generated SDK metadata so a downstream tool (the MCP server
   per `aidocs/56`) can read them off the generated client.
7. **CI-friendly** — runnable from a single CLI command, no
   Java-on-Node.js detour, hermetic builds, Docker image
   available. Pin-one-version-across-languages is the P17
   ask.
8. **Auth helpers** — JWT bearer + API key + OIDC code flow. shepard
   accepts both `Authorization: Bearer <jwt>` and `X-API-KEY: <key>`
   (P6 plans to fold to one); generators differ in how much auth
   plumbing they bake in vs. leave to the caller.

The convenience-wrapper layer (`aidocs/27 / 28`, P16) sits **on top
of** whatever the generator produces. Generator choice doesn't
remove the wrapper; the wrapper hides the four-surface picture
regardless.

---

## 3. The OSS contenders — head-to-head

### 3.1 Comparison table

| # | Tool | Licence | Output languages | OpenAPI 3.1 | Style | Last release | `x-*` ext handling | Fits shepard? |
|---|---|---|---|---|---|---|---|---|
| 1 | **Microsoft Kiota** | MIT | C#, CLI, **Go**, **Java**, PHP, **Python**, Ruby, Swift, **TypeScript** | ✓ native (issue #3914 closed) | **Fluent path-builder** | v1.31.1, 13 Apr 2026 | First-class — `x-ms-kiota-info`, `x-ms-primary-error-message`, generic `x-*` preserved; custom extensions extractable from the generated metadata | **✓ Strong fit** — covers the shipped trio, native 3.1, fluent surface natural for shepard's hierarchical graph |
| 2 | **OpenAPI Generator** | Apache 2.0 | 50+ targets (Java, Python, TS-fetch / TS-axios / TS-angular, Go, C#, Rust, PHP, Kotlin, Swift, Dart, …) | Partial — 3.1 fixes landed incrementally through 7.x but **not** fully native; some templates still trip on `type: [..., "null"]` | Operation-per-method, per-tag API classes | v7.22.0, 28 Apr 2026 | Preserves `x-*` in generated model attributes; per-template idiosyncrasies (some templates expose them, some don't) | **✓ Status-quo fit** — battle-tested, widest reach, but per-template quirks (Python `required` patch, Java `additionalProperties`) |
| 3 | **oapi-codegen** | Apache 2.0 | **Go only** | Partial — 3.1 in flight, not yet feature-complete (per maintainers' Feb 2026 notes) | Go idiomatic, single binary, client+server | v2.6.0, 27 Feb 2026 | Preserves `x-*` via extension-aware templates | **△ Niche fit** — only relevant if §8 picks Go as a target; not a primary |
| 4 | **Hey API (`@hey-api/openapi-ts`)** | MIT | **TypeScript only** (plugin-based: Zod schemas, TanStack Query hooks, fetch / axios / etc.) | ✓ native | Plugin-driven; idiomatic typed-fetch by default; fluent via plugin | Active 2026 (used by Vercel, OpenCode, PayPal) | Preserved + plugin-extensible | **✓ Strong TS fit** — spiritual successor to `openapi-typescript-codegen` (which is now unmaintained) and to the soon-maintenance-mode `openapi-fetch` |
| 5 | **swagger-codegen 3.x** | Apache 2.0 | Same lineage as OpenAPI Generator (forks share templates) | No (3.0 only) | Same as OpenAPI Generator (older templates) | v3.0.x line, still trickle-maintained | Preserves `x-*` but older template surface | **✗ Skip** — OpenAPI Generator is the live fork; swagger-codegen is for legacy users who can't migrate. Included here for completeness only. |
| 6 | **NSwag** | MIT | C# + TypeScript | Partial (.NET-first) | C# idiomatic; TS output narrower than Hey API | v14.7.1, 20 Apr 2026 | Preserves `x-*` | **✗ Skip for v1** — .NET not on shepard's roadmap; revisit only if §8 picks .NET |
| 7 | **datamodel-code-generator + fastapi-codegen** | MIT | Python only (Pydantic models + FastAPI stubs) | ✓ for models (3.1 schemas via JSON Schema 2020-12); fastapi-codegen lags | Pydantic-shaped models | Active 2026 | Preserves `x-*` in Pydantic field metadata | **△ Models-only** — useful if shepard ever wants a typed-models-only Python output without a client runtime; not a full SDK replacement |
| 8 | **openapi-typescript-codegen** | MIT | TypeScript only | Partial | Operation-per-method | **Unmaintained** (Hey API is the fork) | Preserves `x-*` | **✗ Skip** — superseded |
| 9 | **openapi-fetch + openapi-typescript** | MIT | TypeScript only (types-only output → use the `openapi-fetch` runtime) | ✓ native (types-only is the easy case) | Two-layer: typed-`fetch` call sites + generated types | **`openapi-fetch` enters maintenance mode in 2026**; `openapi-typescript` stays active | Preserves `x-*` in type comments | **△ Tactical** — the typed-fetch shape is great DX but the runtime is freezing |

### 3.2 Two non-options worth naming explicitly

- **swagger-codegen 2.x** — last shepard-relevant release lineage
  ended at the OpenAPI Generator fork in 2018. Don't even consider.
- **`typespec-go` / `typespec-python` / `typespec-typescript`** —
  these target TypeSpec source, not OpenAPI. shepard's source of
  truth stays Java annotations on JAX-RS handlers (per `aidocs/23
  §5.5`); we don't move to TypeSpec. So these are off the table
  for v1.

### 3.3 The Kiota fluent shape, concretely

Against shepard's hierarchical surface, the difference is visible:

**OpenAPI Generator (today, Python):**
```python
from shepard_client import ApiClient, Configuration
from shepard_client.api import DataObjectApi
config = Configuration(host=HOST)
config.access_token = TOKEN
client = ApiClient(config)
api = DataObjectApi(client)
do = api.get_data_object_by_id(collection_id=cid, data_object_id=oid)
```

**Kiota (proposed, Python):**
```python
from shepard_client import ShepardClient
sh = ShepardClient(host=HOST, token=TOKEN)
do = await sh.collections.by_collection_id(cid) \
                .data_objects.by_data_object_id(oid).get()
```

The Kiota shape **mirrors the URL path**. Casual users discover
endpoints by tab-completion. Power users who learn `/v2/collections/{appId}/data-objects/{appId}` once read the SDK
the same way. This matches `aidocs/47 §1.0`'s casual-user north
star.

The OpenAPI Generator shape is **fine** — but its discovery
mechanism is "remember which `Api` class holds your method,"
which scales poorly as the surface grows (shepard already has
29 resources / 153 endpoints per `aidocs/26`).

### 3.4 Where OpenAPI Generator still wins

- **Reach.** 50+ language targets. Kiota: 9. If a researcher
  writes Rust / Dart / R, OpenAPI Generator has a template;
  Kiota does not.
- **Maturity.** 8 years of production. Edge cases (oneOf
  discriminators, polymorphic responses, multipart-form uploads
  with named files) are well-trodden. Kiota's edge-case stories
  are still surfacing — see open issues like #6394 (webhook
  generation with 3.1) and #7215 (SSE / `text/event-stream`
  response generation).
- **The bird in the hand.** Three shepard clients ship from it
  today with no behaviour we'd lose.

### 3.5 Where Kiota wins

- **OpenAPI 3.1 native** (since the issue #3914 closure). No
  monkey-patching `nullable`. No Python-specific post-processing
  script. The `patch_openapi_for_python.py` band-aid retires.
- **Fluent path-builder** matches shepard's hierarchical graph.
- **One generator binary, all languages.** Today's pipeline pins
  two OpenAPI Generator versions across three languages because
  per-language fixes land at different rates. Kiota is one tool;
  what works for Python works for Java (modulo language idiom).
- **First-class `x-*` extension story.** Kiota *itself* uses
  `x-ms-kiota-info` to drive generation hints, and exposes
  arbitrary `x-*` extensions in the generated client's metadata.
  For `aidocs/56`'s `x-mcp-tool` / `x-mcp-category` extensions to
  drive an MCP server, the generated SDK is the natural carrier.
- **Backed by Microsoft** for Graph SDK production — not a
  hobby project. Apache-2.0-equivalent licence shape (MIT).

---

## 4. Recommendation

**Dual-generator baseline locked 2026-05-13** (per ADR-0022 / §8):
ship Kiota for `/v2/` and OpenAPI Generator for `/shepard/api/`,
both indefinitely supported. The "primary / secondary" framing
below is shorthand for "new-baseline / still-maintained-legacy" —
neither generator is on a deprecation path.

### 4.1 Primary (new baseline): Microsoft Kiota for `/v2/` clients

**Status — shipped as CG1a (2026-05-13).** The Kiota baseline now lives
at the top-level `clients-v2/` directory; the Makefile pins Kiota v1.31.1;
three languages are wired in this slice: **Python**
(`clients-v2/python-kiota/`, distributed as `shepard-v2-client`),
**TypeScript** (`clients-v2/typescript-kiota/`, distributed as
`@dlr-shepard/v2-client`), and **Java** (`clients-v2/java-kiota/`,
distributed as `de.dlr.shepard:shepard-v2-client-java`). The CI workflow
`.github/workflows/clients-kiota.yml` regenerates the trees on every
release tag + weekly cron + manual dispatch; per-language smoke tests
verify the generated surface against a CI-booted backend. Go / Rust /
C# stretch languages defer to **CG1d**; publication to PyPI / npm /
Maven Central defers to **CG1c**.

Pick **Kiota** as the **primary** generator for the `/v2/` client
shelf. Reasoning, against the §2 criteria:

| Criterion | Verdict |
|---|---|
| Open-source primary | ✓ MIT |
| Multi-language | ✓ Covers Java + Python + TS plus C# / Go / Ruby / Swift / PHP for free |
| Idiomatic output | ✓ Fluent path-builder matches shepard's hierarchical graph |
| OpenAPI 3.1 first-class | ✓ Native since issue #3914 (2024); production-tested in Graph SDK |
| Maintenance health | ✓ v1.31.1, April 2026; weekly-cadence releases; MS-backed |
| `x-mcp-*` friendly | ✓ Best in class — extensions surface in generated metadata, readable by downstream MCP-server work in `aidocs/56` |
| CI-friendly | ✓ Single binary + Docker image (`microsoft/openapi-kiota`); one tool for every language |
| Auth helpers | ✓ Bearer JWT + API key + OIDC code flow built into the runtime libs (`@microsoft/kiota-authentication-*` etc.) |

**Honest downsides** of picking Kiota:

- **Smaller community.** Stack Overflow / blog-post mass is
  ~10× behind OpenAPI Generator. Niche problems take longer to
  find prior art for. Mitigation: Microsoft team is responsive
  on GitHub issues; the Quarkiverse `quarkus-kiota` integration
  exists if we ever want server-side Kiota too.
- **Fewer languages.** No Rust / Dart / R / Kotlin (Kotlin is on
  Kiota's roadmap but not shipped). Mitigation: §4.2 secondary.
- **Generated code is fluent-style only.** Some users (e.g.
  scripting in a Jupyter notebook) prefer the
  `api.get_data_object_by_id(...)` shape over the chained
  builder. Mitigation: the **convenience wrapper** (`aidocs/27`
  P16) ships either way — `sh.collections.list()` etc. — and
  hides the underlying generator shape from casual callers.
- **Edge cases still surfacing.** SSE / `text/event-stream`
  generation (Kiota issue #7215) matters once P13 ships (the SSE
  change-feed in `aidocs/23 §4`). If Kiota's SSE story is still
  thin when P13 lands, the convenience wrapper handles SSE
  directly without going through the generated client — same
  shape as today.

### 4.2 Secondary (still-maintained legacy): OpenAPI Generator for `/shepard/api/...` continuity

Keep **OpenAPI Generator** for the upstream-compat `/shepard/api/...`
clients **as a still-maintained, never-deprecated legacy option**
(per ADR-0022 / §8). Reasoning:

- **Zero-impact for upstream consumers** (`CLAUDE.md` API-version
  policy). Anyone built against `dlr-shepard-clients/*` keeps
  working unmodified.
- The convenience-layer in `aidocs/27` cleans up the boilerplate
  pain for casual users *on either generator*; the per-template
  idiosyncrasies that bother power users are hidden by the same
  wrapper.
- One existing dual-source-of-truth ledger (P17) doesn't get worse
  — the two generators target different `/` prefixes; client
  packages publish under different artifact coordinates (§6).

### 4.3 Secondary, language-specific: Hey API for the TS `/v2/` ride-along

For the `/v2/` TypeScript client specifically, **Hey API
(`@hey-api/openapi-ts`)** is the better tool than Kiota's TS
output for **frontend** consumption — its plugin ecosystem
covers Zod schemas, TanStack Query hooks, and Vue Query (the
shepard frontend is Nuxt 3 / Vue 3 per `aidocs/33`). Kiota's
TS surface is fine for Node-side scripting but doesn't ship
ready-made hook generators.

**Provisional shape:**

- `/v2/` **Node / Deno / Bun** TS client: Kiota TS (unified with
  Python / Java / etc.)
- `/v2/` **frontend TanStack-Query** TS client: Hey API with the
  TanStack Query plugin, consumed only by `frontend/`.

This is a small bet — if Kiota TS gets a TanStack Query plugin
upstream, collapse to one. Documented as an open question (§8).

### 4.4 The convenience wrapper rides on top regardless

`aidocs/27` `shepard-py` / `shepard-ts` and the eventual
`shepard-java` companion are **generator-agnostic**. They wrap
whichever client the build emits. Switching to Kiota for `/v2/`
doesn't invalidate the wrapper design; the wrapper's
`sh.collections.list()` proxies to whatever the underlying
generated client provides.

---

## 5. Commercial alternatives — brief tour

shepard policy is OSS-primary. The candidates below would each
need a separate decision against the licence-compatibility policy
(`CLAUDE.md` security gates) before any adoption. They are listed
to keep the maintainer informed, not as recommendations.

| Tool | One-line | Interesting? |
|---|---|---|
| **Speakeasy** | 10-language SDK generator, standalone binary, no cloud dependency, ships with single-runtime-dep TypeScript output and Zod runtime type-safety; the binary itself is closed, generated SDKs are Apache-2.0. Entry tier $250/mo/SDK. | △ Worth a long look if Kiota's edge-case fragility becomes a maintenance load; explicitly evaluated only after Kiota gets 12 months on production traffic. |
| **Stainless** | Generates the official SDKs for OpenAI, Anthropic, Cloudflare; seven languages; custom configuration DSL on top of OpenAPI. Entry tier $250/mo/SDK. | △ The "feels hand-written" benchmark in the industry. DSL-on-top-of-OpenAPI is a drift risk per `aidocs/23 §5.5`'s objection to TypeSpec. Skip unless DLR is willing to pay for what Kiota and the wrapper deliver for free. |
| **Fern** (Postman-acquired, January 2026) | Nine-language SDK generator + auto-generated API reference docs; OSS core under `fern-api/fern`, but the polished SDK templates are commercial. Acquisition by Postman muddies the long-term licence story. | ✗ Skip — the Postman acquisition introduces vendor-strategy uncertainty; the OSS components alone don't compete with Kiota's surface. |
| **APIMatic** | Long-running commercial SDK + portal generator; pre-Speakeasy / Stainless era; widely deployed at enterprises. | ✗ Skip — older positioning; nothing it does that the OSS field doesn't cover. |
| **Sideko** | YC-batched SDK + docs generator; small. | ✗ Skip — too early for a research-data platform to bet on. |

**Net:** none of the commercial alternatives are interesting
enough to adopt today. If Kiota's fluent + 3.1 + multi-language
combo materially disappoints over 12 months of `/v2/` production
use, **Speakeasy** is the single one worth reopening — its
standalone-binary model is the closest to the OSS workflow.

---

## 6. Migration shape

### 6.1 Distribution today

Upstream ships three generated-client packages from
`gitlab.com/groups/dlr-shepard/-/packages`:

- `de.dlr.shepard:shepard-client` (Maven Central / GitLab packages)
- `shepard-client` (PyPI)
- `@dlr-shepard/client` (npm)

### 6.2 Distribution after Kiota lands

**Status — pipeline shipped (CG1a, 2026-05-13).** Source generation now
happens in `clients-v2/{python-kiota,typescript-kiota,java-kiota}/` via
`clients-v2/Makefile`; the CI workflow re-emits the trees on every
release tag for reproducibility (vendored-into-monorepo per §8 q3). The
distribution names below were chosen to coexist with the
`dlr-shepard-clients/*` upstream packages — operators who only ever
touch the byte-frozen `/shepard/api/...` surface (and thus `clients/`)
see no change. The PyPI / npm / Maven-Central publish step itself is
**CG1c** (not yet shipped); until then, consumers build locally from
`clients-v2/`.

The `/shepard/api/...` byte-compat surface keeps shipping from
the same upstream pipeline. Nothing changes for an admin pulling
the upstream client.

This fork **adds** a parallel `/v2/` client set under fork-owned
artifact coordinates:

| `/shepard/api/...` (upstream) | `/v2/` (this fork) |
|---|---|
| `de.dlr.shepard:shepard-client` (Maven Central) | `io.github.noheton.shepard:shepard-client-v2` (Maven Central) |
| `shepard-client` (PyPI) | `noheton-shepard-client` (PyPI) |
| `@dlr-shepard/client` (npm) | `@noheton/shepard-client-v2` (npm) |

Two packages co-exist in the same Python `venv` / Maven project /
`node_modules`. Different coordinates, different generated
namespaces, zero collision. A consumer who upgrades from
upstream and only ever touched `/shepard/api/...` doesn't even
notice the second package exists.

### 6.3 Where the source lives

The generated source for `/v2/` clients ships from a new top-level
folder under this repo: `clients-v2/{python,typescript,java}/`,
generated from `backend/target/openapi/openapi-v2.json` (per the
P4c per-shelf split). Same shape as the existing `clients/` folder
but for the new surface.

Open question (§8): vendored-into-monorepo vs. published-only.

### 6.4 `aidocs/34` upgrade-tracker impact

The whole CG1 series is **ZERO-impact** for upgrading admins —
additive packages, no existing surface changes. The tracker row
on the PR that lands CG1a notes:

> CG1a — `/v2/` client codegen pipeline (Kiota). ZERO. Upstream
> `dlr-shepard-clients/*` packages keep working. New artifact
> coordinates `io.github.noheton.shepard:shepard-client-v2` /
> `noheton-shepard-client` / `@noheton/shepard-client-v2`
> publish from this fork's CI; opt-in for consumers who want the
> `/v2/` surface.

---

## 7. Phasing — CG1 series

| ID | Slice | Size | Gate |
|---|---|---|---|
| **CG1a** ✓ shipped 2026-05-13 | Kiota CLI pinned at v1.31.1 in `clients-v2/Makefile`; `.github/workflows/clients-kiota.yml` emits Java + Python + TypeScript SDKs from `/shepard/doc/openapi/v2.json` on every release tag + weekly cron + manual dispatch. Vendored source per §8 q3. Per-language smoke tests in `clients-v2/tests/smoke/`. **No publishing yet** — that's CG1c. | M | P4c (shipped) |
| **CG1b** | Publish the SDKs to Maven Central / PyPI / npm under the §6.2 coordinates via the existing release workflow. Versioning tracks the backend's `<revision>` (so client `5.2.0+noheton.4` matches backend `5.2.0+noheton.4`). | M | CG1a; release-workflow signing keys for `io.github.noheton.*` namespace |
| **CG1c** | Golden-output regression: a small "Hello shepard" test client in each language (Java + Python + TS) compiled against the **published** artifact + run against a CI-booted compose stack — same pattern as `aidocs/49` screenshot pipeline. Catches "the generator output works but the published artifact is broken." | S | CG1b |
| **CG1d** | User-facing docs page `docs/reference/clients.md` (per the `docs/reference/*.md` catalogue in `aidocs/49 §2.2`) documenting both client tracks (upstream `dlr-shepard-clients/*` for `/shepard/api/...` and the new `noheton-shepard-client` lineage for `/v2/`); install-line + hello-world per language. | S | CG1b + `aidocs/49` D1c2 |

Recommended order: **CG1a → CG1b → CG1c → CG1d**. CG1a is the
hermetic-codegen step; CG1b adds distribution; CG1c is the
trust-but-verify regression; CG1d closes the docs gap.

**Deferred:**

- **CG1e** (deferred) — Hey API for the frontend TS surface
  (per §4.3). Lands once a frontend slice actually consumes a
  `/v2/` endpoint with TanStack Query and the boilerplate cost is
  measurable.
- **CG1f** (deferred) — `x-mcp-*`-aware SDK metadata. Once
  `aidocs/56` ships its OpenAPI extensions, audit the Kiota
  metadata-surface to confirm the extensions reach a downstream
  consumer (the MCP-server work). Likely a one-line config flip
  in Kiota's `--include-additional-data` mode.
- **CG1g** (deferred) — Retire `patch_openapi_for_python.py`
  band-aid for the *upstream* clients once `smallrye-open-api
  4.x` ships and the backend emits OpenAPI 3.1 natively; OpenAPI
  Generator will need a corresponding 3.1-aware version bump.
  Out of scope for CG1's `/v2/` focus.

---

## 8. Open questions for the maintainer

The three forks-in-the-road:

1. **Kiota vs OpenAPI Generator for `/v2/` (the headline).** **Resolved — 2026-05-13: ship both indefinitely.** Kiota is the **new baseline** for `/v2/` client generation (per-shelf v2 OpenAPI doc → Kiota → `clients-v2/`); OpenAPI Generator stays the **still-maintained legacy option** for the byte-frozen `/shepard/api/` surface (per-shelf v1 OpenAPI doc → OpenAPI Generator → `clients/`). Neither is on a deprecation path; the maintainer's intent is that an operator can keep using either client generation today and pick the one that fits their tooling. Decision recorded in **ADR-0022** (`aidocs/63`). Implementation lands in **CG1a** (Kiota baseline) + **CG1b** (OpenAPI Generator legacy maintenance).
2. **Java + Python + TypeScript only — or stretch to Rust / Go for
   the engineering-physics community?** Kiota covers Go natively;
   it does **not** cover Rust (no template). If Rust matters,
   either (a) ship Rust via `oapi-codegen` is wrong (oapi-codegen
   is Go-only); the actual Rust path is OpenAPI Generator's
   `rust` template — which works but means accepting two
   generators on `/v2/`; or (b) defer Rust client and accept
   that Rust users `cargo add reqwest` and use the REST surface
   raw until shepard's user demand justifies a deeper investment.
   The Speakeasy commercial path covers Rust + 9 others if the
   maintainer ever wants to revisit §5.
3. **Vendored source vs publish-only.** Two shapes:
   - **Vendored** — generated `clients-v2/<lang>/` source is
     committed to the monorepo on every `main` rebuild (same
     shape as today's `clients/`). Pro: single source of truth,
     PR diffs show what the API change looked like in client
     code, contributors can poke at the generated source.
     Con: large PR diffs on every API change; harder to roll
     back published-vs-source drift.
   - **Publish-only** — CG1a's CI job emits SDKs into a build
     artefact, CG1b publishes, source never lands on `main`.
     Pro: lean repo; the package registry is the source of
     truth. Con: the generated client only exists at
     release-tag time; contributors can't read it from a
     branch checkout.

   This design **tentatively recommends vendored** (matches
   current shepard practice — `clients/` is in the upstream
   repo today) but the maintainer should confirm.

---

## 9. Cross-references

- **`aidocs/16`** — P17 ("pin generator + Kiota PoC") becomes the
  CG1 series; P17b (CI schema-name lint, shipped) stays where it
  is. New CG1a–CG1d rows track this design's phasing.
- **`aidocs/23 §5`** — original client-generation critique;
  this doc is the resolved version of "stay with OpenAPI
  Generator + prototype Kiota in parallel" — the prototype
  outcome is the recommendation in §4.
- **`aidocs/27`** — convenience wrappers (`shepard-py` /
  `shepard-ts`); generator-agnostic; sit on top of CG1's
  output.
- **`aidocs/28`** — paradigms-and-clients synthesis; this doc
  fills in the generator slice that §3 of `aidocs/28` named
  but didn't resolve.
- **`aidocs/40 §4`** — cross-tool OpenAPI versioning concerns
  (sTC / SPW also consume generated clients); the §6.2 split
  applies to them too.
- **`aidocs/42 §"Where it's going"`** — researchers consume
  shepard through clients; cleaner SDKs is a vision-level win.
- **`aidocs/44`** — new "v2 client codegen" row under §15 API
  surface, status `📐 designed` on this PR's merge.
- **`aidocs/47 §4.5`** — DX5 OpenAPI hot-reload; the
  CG1a-emitted codegen step plugs into Quarkus dev-mode so a
  backend signature change auto-refreshes the local
  `clients-v2/` source.
- **`aidocs/49 §2.2`** — the `docs/reference/clients.md` page
  for CG1d is named in the §2.2 reference catalogue.
- **`aidocs/56`** — sibling MCP-tool-naming design;
  `x-mcp-*` OpenAPI extensions must survive Kiota generation
  (confirmed by §3.1 row 1; CG1f verifies on landing).
- **`CLAUDE.md`** — API-version policy (`/v2/` only),
  licence-compatibility policy (Apache-2.0 / MIT only for
  `/v2/` codegen toolchain).

---

## 10. What this isn't

- **Not** picking the MCP-server framework — that's `aidocs/56`'s
  job. This doc only ensures the generator chosen here doesn't
  block the MCP work.
- **Not** building our own generator. The reach + maintenance
  cost of every OSS contender in §3 makes a homegrown generator
  unjustifiable; we pick one and use it.
- **Not** migrating shipped clients overnight. The
  `/shepard/api/...` clients ship from upstream's existing
  OpenAPI Generator pipeline; they keep doing that. CG1 is
  additive on `/v2/`.
- **Not** moving the schema source of truth off JAX-RS
  annotations. `aidocs/23 §5.5` already resolved that —
  annotations stay primary; we don't go to TypeSpec / Smithy /
  Fern-DSL.
- **Not** a commitment to ship Kiota in the next release. CG1a
  lands the pipeline + smoke-test; CG1b is the publish step
  the maintainer gates on the §8 open questions being answered.
- **Not** retiring the convenience-wrapper plan (`aidocs/27 / 28`
  P16). The wrapper still ships; the generator below it switches.
