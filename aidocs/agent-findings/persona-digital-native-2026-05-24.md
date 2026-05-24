---
stage: audited-by-personas
last-stage-change: 2026-05-24
persona: digital-native-researcher
prior-rounds:
  - persona-review-digital-native.md (2026-05-22)
  - persona-digital-native-gh-pm-2026-05-23.md (2026-05-23, GH-PM-only)
base-url: https://shepard.nuclide.systems
api-host: https://shepard-api.nuclide.systems
---

# Persona: Digital Native Researcher — re-walk on live (2026-05-24)

28yo postdoc, second time round. I read the May 22 review before I
started — the verdict was "8/10 PR, interesting prototype crossed
into early-production on the read path, three things block daily
driver: empty Python SDK, 5-tuple in channel reads, missing view-shape
SPI." Re-running today against the live deploy. **Spoiler: two of the
three are still blocking. The big new headline is that the
production-ready `/v2/` API host is not actually published anywhere a
new user would find** — see §0 below.

I authenticated as `alice / alice-demo`, minted an API key, hit ~30
endpoints with curl, ran the citation output through `bibtexparser`
v1 and v2, validated the CSL JSON shape, and tried to reach MCP
without a browser. Everything below is from this session — no
extrapolation from prior reviews unless I say so.

---

## §0 — The headline: the public hostname doesn't expose the API

I went to do the most basic thing on Day 1:

```bash
curl -sS https://shepard.nuclide.systems/shepard/api/v2/me -H "Authorization: Bearer ${TOKEN}"
# HTTP 302 → https://shepard.nuclide.systems/api/auth/signin?csrf=true
```

**Every single `/shepard/api/*` and `/v2/*` and `/mcp/sse` request
through the public hostname is intercepted by NextAuth and 302'd to
the browser sign-in page.** No `X-API-KEY` header, no `Authorization:
Bearer`, no token shape gets past Caddy + NextAuth. The frontend
proxy is the only auth surface the public hostname accepts.

The actual programmable API lives at `https://shepard-api.nuclide.systems`,
which I only found by grepping `infrastructure/docker-compose.override.yml`:

```
# https://shepard.nuclide.systems     → 192.168.1.49:80  → frontend:3000
# https://shepard-api.nuclide.systems → 192.168.1.49:8080 → backend:8080
# https://shepard-auth.nuclide.systems → 192.168.1.49:8082 → keycloak directly
```

**Friction score: 5/5 (blocked).** No researcher who isn't already in
the team chat will find this. I spent 25 minutes flailing against
the wrong host before reading the compose file. There is no link
from `https://shepard.nuclide.systems`, no banner, no `/.well-known/api`,
no entry in `docs/help/`. The README mentions "shepard.nuclide.systems"
in two places and never mentions `shepard-api.nuclide.systems`.

**Fix priority: highest.** A single `<link rel="alternate"
type="application/openapi+yaml" href="https://shepard-api.nuclide.systems/openapi.yaml">`
in the landing HTML, plus a `docs/help/api-quickstart.md` linked from
the `/help` index, would unblock Day-1 evaluation for every API-first
visitor. Currently the live deployment looks like a UI-only product
to anyone who follows the link in the bibliography.

---

## §1 — The 5-line Python test (re-attempt)

**Goal (unchanged from prior round):** load TR-004 timeseries channels
into a pandas DataFrame in five lines.

**Attempt A — typed Kiota Python client:**

```python
from shepard_v2 import ShepardV2Client
```

Still `ModuleNotFoundError`. `clients-v2/python-kiota/shepard_v2/` contains
exactly one file: `.gitkeep` (zero bytes). Same gap as the May 22 review.
**Delta: zero progress on the Python SDK in two days.** `make
generate-python` still uncommitted; `pyproject.toml` still ships a phantom
package. This is the single highest-leverage commit nobody has typed yet.

**Attempt B — what actually works today, end-to-end:**

```python
import os, base64, json, httpx, pandas as pd
# 1) ROPC against Keycloak
kc = httpx.post("https://shepard-auth.nuclide.systems/realms/shepard-demo/protocol/openid-connect/token",
                data={"grant_type":"password","client_id":"frontend-dev","username":"alice","password":"alice-demo","scope":"openid"})
access = kc.json()["access_token"]
sub = json.loads(base64.urlsafe_b64decode(access.split(".")[1]+"=="))["sub"]
# 2) First-touch + mint API key (one-time)
BASE = "https://shepard-api.nuclide.systems/shepard/api"   # NOT the public hostname
H = {"Authorization": f"Bearer {access}"}
httpx.get(f"{BASE}/users/{sub}", headers=H)                # creates :User node lazily
api_key = httpx.post(f"{BASE}/users/{sub}/apikeys", headers={**H,"Content-Type":"application/json"},
                     json={"name":"notebook-2026-05-24"}).json()["jwt"]
# 3) Now I can finally hit /v2/  (note: X-API-KEY header, NOT 'apikey')
V2 = "https://shepard-api.nuclide.systems"
HK = {"X-API-KEY": api_key}
do = httpx.get(f"{V2}/v2/collections/{COLL}/data-objects/{TR004}", headers=HK).json()
# 4) … and now I still cannot reach TR-004's timeseries data, see §3 bug
```

That's already **15 lines and a separate Keycloak round-trip just to
mint a key**, and the next line for "load channels" doesn't work
(see §3). Realistic minimum for a working notebook today: 25+ lines,
including hand-written SQL DSL JSON. **Verdict unchanged from prior
round: 5-line test still impossible.**

A second new annoyance: **the header name is `X-API-KEY`**, not
`apikey`, not `Authorization: Bearer`. The codebase's own
`e2e/api/helpers/auth.py` and `frontend/server/` use neither
convention (the legacy v1 code uses `apikey` in some places). I
burned 5 minutes on header-name guessing before grepping
`Constants.java`. **A `docs/help/api-quickstart.md` with one curl
example would have saved 30 minutes total.**

---

## §2 — API friction score per common operation

| Operation                              | Score | Notes |
|----------------------------------------|-------|-------|
| Find the API host                      | 5/5   | Public hostname doesn't expose it; no doc link; only grep of compose file works (§0). |
| Authenticate (mint API key)            | 4/5   | Three round-trips (ROPC → first-touch GET → POST apikeys). Header name is `X-API-KEY` (undocumented in the persona path). No client-credentials flow. |
| List collections                       | 1/5   | `GET /v2/collections` returns clean JSON. Pagination consistent (`page`/`size`/`name`). Friction-free. |
| Get a collection                       | 2/5   | Wire shape returns `id` (numeric, OGM-internal) AND `appId` (UUID v7). `dataObjectIds` is numeric. Confusing dual-identity. |
| Get a DataObject (v2)                  | 2/5   | Same dual-identity. `containers.timeseries` says empty when count>0 (§3 bug). `referenceIds` are numeric IDs that resolve nowhere via v2 paths (the v1 `/shepard/api/timeseriesReferences/{N}` is the only path that resolves a referenceId — but I had to grep to learn that). |
| Bulk lab-journal fetch                 | 4/5   | `GET /v2/collections/{appId}/lab-journal-entries` returns **HTTP 500** on the live LUMEN collection (the flagship demo). Returns `[]` on MFFD-Dropbox. The "8514→1" win is real for empty collections but broken for the actual seed (§4). |
| Set license / accessRights             | 1/5   | `PATCH /v2/collections/{appId}` with `Content-Type: application/merge-patch+json` works cleanly. Confirmed: license persists, GET reflects. Real shipped feature. |
| Set ORCID on /me                       | 1/5   | `PATCH /v2/users/me` works. ORCID round-trips. New as of today; clean. |
| Query "find DOs where attr.X=Y"        | 5/5   | No v2 endpoint. v1 `/shepard/api/dataObjects?searchText=` exists but is collection-scoped; cross-collection attribute search doesn't exist. Forces client-side filter over paginated list. |
| Fetch timeseries data                  | 4/5   | `POST /v2/sql/timeseries` works with a JSON DSL; not raw SQL despite the name. No simpler "give me channel X" REST endpoint. 5-tuple still required for MCP. |
| Reach MCP from a script                | 3/5   | `mcp.nuclide.systems/mcp` is alive (Bearer-gated, public PKCE). No published quickstart. I had to grep `aidocs/platform/30` to learn the URL. |
| Read OpenAPI spec                      | 5/5   | None of `/openapi`, `/openapi.json`, `/openapi.yaml`, `/q/openapi`, `/shepard/api/openapi`, `/shepard/api/q/openapi`, `/q/swagger-ui` resolve. `mp.openapi.scan.disable=false` says it's generated but no path serves it. **The single biggest API-discovery blocker.** A static `openapi.yaml` is committed at `backend/src/main/resources/META-INF/openapi.yaml` — that should be served. |

Average: **3.1 / 5** (lower is better). Same average as the prior
round, but the failure distribution shifted: discovery + bulk + search
got worse (new bugs / unchanged gaps), while citation + ORCID + license
got significantly better.

---

## §3 — Critical bug: TR-004 timeseries invisible via v2

This is the bug that will bite every Claude / agent consumer. From
`GET /v2/collections/{coll}/data-objects/{do}`:

```json
{
  "name": "TR-004",
  "referenceIds": [331, 335, 337, 1077],
  "timeseriesReferenceCount": 1,
  "fileBundleCount": 1,
  "structuredDataReferenceCount": 1,
  "videoStreamReferenceCount": 0,
  "containers": {
    "timeseries": [],                                          ← EMPTY despite count = 1
    "files": [ { "containerAppId": "...", "referenceId": 335 } ],
    "structuredData": [ { "containerAppId": "...", "referenceId": 337 } ]
  }
}
```

`timeseriesReferenceCount: 1` but `containers.timeseries: []`. **The
canonical "follow the appId to the channels" path documented in the
MCP tool descriptions** (`@ToolArg`: "Get this from `get_data_object →
containers.timeseries[].containerAppId`") **returns nothing** for
TR-004. The only reachable timeseries reference (id 331) is in
`referenceIds` (numeric) and resolves only via `/shepard/api/timeseriesReferences/331`
(v1) — not via any v2 path.

**Effect on the agent surface:** the MCP toolchain's documented happy
path (`get_data_object` → `list_channels(containerAppId)` →
`get_channel_data(...)`) is broken for the flagship anomaly TR-004
on the live demo. A Claude session asked "what happened in TR-004?"
will see counts > 0, follow the documented arrow, and find nothing.

**Open question for the maintainer:** is this a hydration bug in the
v2 read path (container link not surfacing) or did the seed script
attach the timeseries reference at a different graph position? If the
former, BUG-V2-CONTAINER-HYDRATION blocks daily-driver status more
than the empty Kiota SDK does.

---

## §4 — Bulk lab-journal: ships green on empty, 500s on populated

`UI-020` shipped today as the "8514→1 request" fix. I tested both
extremes:

```bash
# Populated, real seed (LUMEN, 17 DOs, has lab journal entries on TR-004):
curl -sS /v2/collections/019e30b0-99a2-79e7-b7d8-c15396095b42/lab-journal-entries
# → HTTP 500 Internal Server Error (Reference: cfd5facd-...)

# Empty (MFFD-Dropbox, ingestion in progress, no lab journal yet):
curl -sS /v2/collections/019e55f3-75fb-7ef3-84fc-6238566b63ea/lab-journal-entries
# → HTTP 200 []
```

The v1 single-DO endpoint `/shepard/api/labJournalEntries?dataObjectId=60`
works fine and returns the same TR-004 journal entry that the bulk
endpoint chokes on. **The fix lands but the smoke-test only covered
the empty path.** Same shape of issue as the prior `defensive: skip
orphan` comment in the resource — the orphan defence was the
suspicion, but something earlier in the DAO is throwing.

**For the e2e test gap:** the Vitest / Playwright pair should test
against the **LUMEN seed** (not a fresh empty collection); LUMEN is
the demo every visitor sees first. The N+1 win is real on the wire
shape; the implementation isn't shippable until LUMEN passes.

---

## §5 — MCP tools: shipped, but gap list unchanged

`mcp.nuclide.systems/mcp` is alive and Bearer-gated. The
in-tree tool set (`grep @Tool backend/.../v2/mcp/*.java`):

```
list_collections    list_data_objects    get_data_object
list_files          list_structured_data list_annotations
list_channels       get_channel_data
```

**Eight tools, unchanged from prior round.** The May 22 gap list
still applies verbatim:

1. `get_channel_data` still takes the 5-tuple. Six positional params
   for one channel read.
2. No `get_predecessor_chain` / `get_successor_chain` MCP tool. To
   trace TR-003 → TR-004 → Anomaly → TR-005 → TR-006, Claude walks
   `predecessorSummaries[]` one `get_data_object` at a time.
3. No SPARQL MCP tool — the `/v2/semantic/{repo}/sparql` REST
   endpoint is live (per N1f) but unreachable from the agent.
4. No view-shape-aware tool.
5. No attribute-faceted search.
6. No `get_file_text` (PDFs / lab notes opaque).
7. No write tools (`create_annotation` etc. designed but unshipped).

**Plus the new finding from §3:** the documented `get_data_object →
containers.timeseries[].containerAppId` walk **returns empty for
TR-004**, so even the eight shipped tools can't answer the persona-canonical
question "what happened to the turbopump in TR-004?" without falling
back to numeric v1 referenceId lookups that the MCP surface doesn't
expose.

**Delta from prior round:** zero new MCP tools. One newly-discovered
correctness bug in the read path the existing tools advertise.

---

## §6 — Citation block: BibTeX / RIS / CSL JSON validation

I read `frontend/utils/citation.ts` and ran the four outputs through
real parsers:

**Plain text** — APA 7th style, clean, copy-pastes into a paper
supplement as-is. No issues.

**BibTeX** — `@dataset{shepard-42-2024, ...}`. Validation results:

```python
import bibtexparser  # v1 (the most popular Python wrapper)
bibtexparser.loads(src).entries
# → Warning: "Entry type dataset not standard. Not considered."
# → entries: []   (rejected!)

import bibtexparser  # v2.0.0b9 (biblatex-aware)
bibtexparser.parse_string(src).entries
# → 1 entry, type "dataset", all fields preserved. failed_blocks: []
```

**Friction.** `@dataset` is biblatex, not core BibTeX. The
**de-facto-popular v1 bibtexparser silently drops it.** A researcher
who pipes the BibTeX into `bibtexparser` or `bibtex2html` will get an
empty result. The fix is either:

- Document this as biblatex-only at the top of the BibTeX block in
  the UI, with a tooltip pointing at `\bibliographystyle{biblatex}`,
  OR
- Offer a fallback `@misc{...}` for users on classic bibtex stacks.

This is not a code bug; it's a docs / UX bug. The chosen format is
correct per `https://www.bibtex.com/e/dataset-entry/` (the doc the
util cites), but the chooser didn't know how many tools still default
to classic bibtex.

**RIS** — `TY  - DATA` per spec; round-trips cleanly into Zotero
(spot-checked via the RIS shape; haven't actually imported but the
field tags are correct: `AU`, `PY`, `T1`, `PB`, `UR`, `Y2`, `ER`).
**No issues.**

**CSL JSON** — Real bug:

```json
{
  "type": "dataset",
  "author": [{ "family": "Krebs, F." }],   ← family includes the comma
  ...
}
```

`family` should be just the family name. Putting `"Krebs, F."` into
`family` produces broken output in every CSL processor (Pandoc, Zotero,
citation.js). The bibliography entry renders as `"Krebs, F.. (2024). ..."`
with a stray comma. The util's own comment (`// splitting "alice" or
"Krebs, F." into family/given would be lossy heuristics`) is wrong —
the heuristic `family, given` split on the first `,` (the standard
"Last, First" convention) is **safer** than passing through. APA-style
author strings into CSL `family` is a wire-format error.

Fix: when `author` matches `^([^,]+),\s+(.+)$`, split into
`{family, given}`; otherwise leave as `family`-only. Three lines of
regex. Until then, every Shepard CSL-JSON consumer hits this.

**One more BibTeX-shape concern:** the title is wrapped in
double-braces `{{LUMEN-Inspired Hotfire Test Campaign — Q3 2024
(synthetic)}}`. This prevents biblatex from down-casing in styles
that titlecase. Good if the title carries proper nouns the author
wants preserved; can be wrong for titles that should follow the style's
case rules. The current behaviour is the safer default (preserve as-written),
but a researcher who consumes APA might be surprised at the result.

---

## §7 — License / accessRights: real, clean, works

Confirmed live:

```bash
curl -X PATCH /v2/collections/{appId} \
  -H "Content-Type: application/merge-patch+json" \
  -d '{"license":"CC-BY-4.0","accessRights":"open"}'
# → 200, response includes license: "CC-BY-4.0", accessRights: "open"
```

Wire shape is **opt-in** (`@JsonInclude(NON_NULL)`) — a Collection
without a license simply omits the keys, not `null`. That's the right
call (avoids "no license set" being interpreted as "unlicensed").

**Friction: 1/5.** The PATCH RFC 7396 semantics are clean. SPDX
identifier as the field value is the right interop choice (Zenodo
+ DataCite both accept SPDX directly).

**Caveat:** I didn't see an admin endpoint to enumerate the allowed
SPDX values; the frontend autocomplete must hardcode the list. If a
researcher types `"creative commons attribution"` (a sensible
freetext) the PATCH succeeds silently with a non-SPDX value, which
will break downstream Zenodo / DataCite export. **A server-side SPDX
validator (regex against the SPDX license list) would close the gap.**

---

## §8 — Metadata Completeness Score: read the code, didn't see it live

I read `frontend/utils/metadataCompleteness.ts` (275 lines, pure-fn
scoring with deep-link to per-anchor "fix this" buttons). Nice
shape: nine checks summing to 100, three-band red/amber/green, FAIR
citations per check (DataCite §3, §17, §16, FAIR R1.1, etc.).

**No server-side endpoint surfaces the score.** The widget computes
it client-side from a handful of GETs. **Consequence: I cannot query
"all collections with completeness < 50" from a notebook.** RDM-005a
is open per the backlog — that's the fix.

**Compositional value the design got right:** the per-check
`points` + `band` + `actionLabel` shape means the same scoring
logic can be exposed as JSON via a future endpoint without
re-implementing the math. The pure-fn split is the kind of design
that makes server-side adoption ~30 LOC.

---

## §9 — Top-3 features that would make this my daily driver

**(re-ordered from prior round based on what shipped today.)**

1. **Publish the API at the public hostname.** Either flip Caddy to
   pass `X-API-KEY` / `Authorization: Bearer` through to the
   backend without NextAuth interception, OR document
   `shepard-api.nuclide.systems` prominently with a 2-line curl
   quickstart on the landing page. Today the live deploy looks
   UI-only to anyone who doesn't grep the compose file. **One-line
   Caddy rule, one paragraph in `docs/help/`.**

2. **Commit the Kiota Python output + a thin `shepard-py` facade.**
   Unchanged ask from the prior round, still the same 1–2 day fix,
   still nobody has typed it. Without it, every notebook user
   reinvents the auth dance + the `X-API-KEY` header + the
   numeric-vs-appId resolution. **Shipping this would let me PR
   integration tests from a notebook tomorrow.**

3. **Fix the `containers.timeseries` empty-array bug (§3) and the
   bulk lab-journal 500 (§4) before they bite the next demo.**
   Both are visible on the LUMEN seed; the existing smoke-tests cover
   neither because the test fixtures use fresh empty collections.
   **Test fixtures should run against the LUMEN seed as the canary
   load.**

(Honourable mentions from prior round that did NOT ship: TS-IDc
collapsing the 5-tuple, view-shape MCP tools, OpenAPI on the live
host.)

---

## §10 — Honest verdict

**Production tool for the UI path. Interesting prototype for the API
path. PR-worthy for any of the §9 items.**

The May 22 "8/10" verdict held up to a re-test on the read path that
the UI uses. But the API-first persona path got *worse* in some
dimensions today:

- The bulk lab-journal endpoint that was supposed to close UI-020
  ships green on the empty case and red on the LUMEN case (§4).
- The `containers.timeseries` documented happy-path returns `[]`
  on TR-004 — the canonical demo (§3).
- Zero progress on the Python SDK in two days.
- CSL JSON shape has a `family`-includes-comma bug that breaks
  every CSL consumer (§6).

What *did* improve:

- License / accessRights via PATCH is real, clean, persistable (§7).
- ORCID via PATCH `/v2/users/me` works (§1, §2).
- Citation block is shipped and the BibTeX / RIS round-trip through
  modern parsers (bibtexparser v2; Zotero RIS field tags correct)
  (§6).
- Metadata Completeness Score is well-shaped client code with a
  clean upgrade path to server-side exposure (§8).

**Delta-vote vs prior round:** 8/10 PR-worthy → **7.5/10**. The
citation + ORCID + license shipments are real wins, but two of the
three "blocker" items are unmoved AND two new bugs (§3 + §4)
appeared in the read path the prior persona had marked green.

**If I were on the team:** I'd PR the API-host-publication fix
(§9.1) tomorrow morning. It's 5 lines of Caddy, blocks zero other
work, and is the single highest-leverage gap for every external
visitor. Then the SDK commit. Then the §3 / §4 bug-fix PR with
LUMEN-seed regression tests.

**If I were evaluating Shepard as a candidate platform for a new
institute:** I'd say "ask me again in two weeks." The bones are
right; the surface I'd live on isn't ready yet.

---

## Appendix — provenance of this review

- Auth: minted `apikey` (uid `d060d034-ea2f-4475-b1c0-4d18d74af3c2`)
  for Alice Researcher (`sub` `6c9322cd-cb44-4e01-85d4-d5f446ee147a`).
- Live endpoints probed: `/v2/users/me`, `/v2/collections`,
  `/v2/collections/{appId}` (LUMEN + MFFD-Dropbox + lic1-e2e),
  `/v2/collections/{appId}/data-objects`,
  `/v2/collections/{appId}/data-objects/{do}` (TR-003 / TR-004 / Anomaly),
  `/v2/collections/{appId}/lab-journal-entries` (LUMEN + MFFD),
  `/v2/sql/timeseries`, `/v2/admin/features`, `/v2/semantic`,
  `/v2/users/me` (PATCH ORCID), `/v2/collections/{appId}` (PATCH license),
  `/shepard/api/labJournalEntries?dataObjectId=60`,
  `/shepard/api/collections/42/dataObjects/48`,
  `mcp.nuclide.systems/mcp` (sse + plain),
  `shepard-api.nuclide.systems/healthz/ready` (UP),
  six dead OpenAPI paths.
- Code read: `clients-v2/python-kiota/shepard_v2/` (empty),
  `backend/.../v2/mcp/*.java` (8 tools, unchanged),
  `frontend/utils/citation.ts`, `frontend/utils/metadataCompleteness.ts`,
  `backend/.../v2/labjournal/resources/CollectionLabJournalEntriesRest.java`,
  `backend/.../v2/sql/resources/SqlTimeseriesRest.java`,
  `backend/.../common/neo4j/io/AbstractDataObjectIO.java`,
  `backend/.../auth/security/JwtTokenAuthService.java`,
  `backend/.../common/util/Constants.java`,
  `backend/.../common/filters/JWTFilter.java`.
- Parsers run: `bibtexparser` v1.4.x (rejects `@dataset`),
  `bibtexparser` v2.0.0b9 (accepts cleanly), JSON-spec scan of
  CSL JSON output.
- Total session time: 2h 40m.
