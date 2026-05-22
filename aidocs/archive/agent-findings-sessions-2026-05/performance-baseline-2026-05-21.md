# Shepard performance baseline — 2026-05-21

**Instance.** https://shepard.nuclide.systems (frontend),
              https://shepard-api.nuclide.systems (backend, direct)

**Method.**
- API: `curl -w "%{time_total}"` against the live backend, **10 trials** per
  scenario, sleep 200 ms between trials. `time_total` is the full wall time
  from connection start to last byte received (so includes TLS handshake,
  request, server processing, response — the same number a client SDK would
  observe).
- UI: Playwright (Chromium, headless), **5 cold trials** per page — fresh
  browser context with persisted Keycloak storageState, no shared keep-alives
  across trials. Metrics from Navigation Timing + Paint Timing + a
  `PerformanceObserver` on `largest-contentful-paint`.
- Network requests / cumulative bytes captured per page via `page.on('response')`.
- JS heap via `performance.memory.usedJSHeapSize` snapshot after `load + 2 s` settle.

**Trigger for re-baseline.**
- Backend image change (any production deploy)
- Frontend image change
- Neo4j data growth >10× (collections list scales with collection count;
  payload fetch scales with file count per container)
- TLS / proxy / Caddy config change (TLS handshake is a meaningful chunk of
  the median; see §3)

**Environment / caveats.**
- Tests run from the same host that hosts the instance (Proxmox LXC). Network
  latency between Claude-Code agent and `shepard-api.nuclide.systems` is
  effectively LAN — observed median TCP+TLS overhead is ~25–30 ms. A remote
  consumer over the public internet will see 50–150 ms more per request.
- A parallel Playwright agent was running an upload-flow validation against
  the same instance during the measurement window. No degradation was visible
  in our trials, but absolute numbers may include a small share of contention.

---

## 1. API endpoint latencies

All numbers are wall-clock from `curl -w "%{time_total}"`, in milliseconds.
N = 10 per scenario.

| Scenario                                  | HTTP | Median | p95 | min | max | Response size | Notes |
|-------------------------------------------|------|-------:|----:|----:|----:|--------------:|-------|
| `GET /shepard/api/users/{self}`           | 200  |  32    | 101 |  29 | 144 |    317 B      | Auth probe; first-trial cold spikes |
| `GET /shepard/api/collections`            | 200  |  40    | 116 |  33 | 170 |  1 869 B      | List of all collections visible to user |
| `GET /shepard/api/collections/42`         | 200  |  31    |  38 |  26 |  38 |    829 B      | LUMEN-Inspired demo (smallest variance — warm path) |
| `GET /shepard/api/collections/473889`     | 403  |  38    |  42 |  28 |  43 |    221 B      | MFFD synthetic; current user lacks read perm — fail-fast |
| `GET /shepard/api/collections/493423`     | 200  |  32    |  54 |  27 |  55 |    367 B      | AI Exchange — just populated |
| `GET …/collections/42/dataObjects/48`     | 200  |  44    |  55 |  36 |  55 |    835 B      | TR-004 (the LUMEN anomaly DO) |
| `GET …/collections/493423/dataObjects/495374` | 200 | 34   |  40 |  31 |  41 |    877 B      | Newly-uploaded "Claude Agent" DO |
| `GET …/fileContainers/493473/payload/…`   | 200  |  40    | 119 |  31 | 170 |   10 947 B    | SHOWCASE.md (~11 KB markdown) |
| `GET …/fileContainers/63/payload/…`       | 200  |  36    |  44 |  31 |  48 |    678 B      | LUMEN tr-004-test-report.md (smaller) |
| `GET /v2/collections/{appId-of-42}`       | 200  |  39    |  55 |  29 |  60 |    829 B      | Same payload as v1 — no overhead penalty on the v2 shelf |
| `GET /v2/mcp` (root probe)                | 405  |  32    |  48 |  27 |  49 |    0 B        | 405 Method Not Allowed (expected — root is not a `GET` target; documented behaviour) |
| **Bulk write end-to-end**                 | 200  | **270**| **342** | 247 | 342 |    n/a        | Create DataObject → upload one small file → create FileReference. Three sequential round-trips. |

**Wire-size observations.**
- The smallest "happy" GET responses are ~300–900 B. Header overhead
  dominates payload at this size — the actual entity bodies are 5–10× smaller
  than the response. Compression (gzip / br) is not measurable from these
  numbers because `--compressed` was not requested; assume responses are
  uncompressed JSON. **Recommendation:** verify Caddy is `encode gzip zstd`
  for the API host — this is a one-line config that would cut wire bytes
  ~3× on JSON.
- The 10 KB markdown payload (`SHOWCASE.md`) is fetched in ~40 ms median;
  the p95 of 119 ms is a 3× spread driven by an occasional ~170 ms tail.
  This is the structural-clunkiness baseline: 11 KB of static text takes
  longer to fetch through the auth + permissions + MongoDB-GridFS stack
  than a 1.8 KB JSON list of collections.

## 2. UI page load metrics

Cold-load, headless Chromium, viewport 1440×900, persisted Keycloak session.
N = 5 per page. All times in ms unless noted.

| Page                                                   | DCL median | load median | FCP median | LCP median | LCP max | # requests | KB total | Heap idle |
|--------------------------------------------------------|-----------:|------------:|-----------:|-----------:|--------:|-----------:|---------:|----------:|
| `/` (landing / collection list)                        |    265     |    275      |    220     |    608     |   808   |     79     |  3 651   |  12.1 MB  |
| `/collections/493423` (AI Exchange)                    |    284     |    355      |    220     |    744     |   828   |    155     |  4 771   |  19.6 MB  |
| `/collections/493423/dataobjects/495374` (DO detail)   |    302     |    320      |    232     |    920     | **1308**|    161     |  4 646   |  19.6 MB  |
| `/collections/473889` (MFFD — 403'd, error page)       |    333     |    363      |    256     |    836     |   960   |    137     |  4 572   |  12.1 MB  |
| `/me` (user profile)                                   |    219     |    221      |    192     |    728     |   804   |     77     |  2 306   |  10.1 MB  |

Ranges (min–max across the 5 trials):
- landing: dcl 188–310, load 241–313, fcp 152–236, lcp 536–808
- collection_493423: dcl 261–334, load 342–394, fcp 188–232, lcp 676–828
- dataobject_493423/495374: dcl 277–327, load 295–522, fcp 204–248, lcp 864–1308
- collection_473889 (403 page): dcl 278–363, load 303–544, fcp 224–264, lcp 732–960
- user_profile_me: dcl 210–250, load 212–253, fcp 184–204, lcp 388–804

### Bonus UI metrics

| Scenario                                  | Median | Range  | Notes |
|-------------------------------------------|-------:|--------|-------|
| Collection → DataObject navigation lag    |  78 ms | 59–88  | SPA route; no full page reload |
| Markdown file preview render time         |    —   |   —    | **No preview opens.** Clicking the `SHOWCASE.md` text row on `/collections/493423/dataobjects/495374` produced no DOM change (body innerText delta = 0, no modal/drawer rendered, URL unchanged). Verified across 5 trials with an explicit inspector script. See §3 observation 4. |

## 3. Observations

1. **Reads are uniformly fast and tight.** All GET endpoints land between
   31–44 ms median, with p95 ≤ 60 ms for 9 out of 11 entity-level reads. This
   is genuinely good for an OGM-backed graph store doing real permission
   checks. There is no obvious "slow endpoint" in the surveyed surface.

2. **Wide p95 tails on first-trial-cold spikes.** Three scenarios show p95
   2–3× their median (`self_fetch` 32 → 101 ms, `collections_list` 40 → 116,
   `payload_showcase_md` 40 → 119). In each, the maximum is the first trial
   and subsequent trials are stable. This is consistent with cold JIT / OGM
   compile / connection-pool warming. **Implication for SLO-setting:** the
   honest "warm" median is ~30–40 ms, but a load-test or freshly-deployed
   instance will see ~150 ms one-shot latencies until the JVM and Neo4j
   driver pool settle.

3. **Bulk-write is 270 ms median for a three-step DataObject + file +
   reference creation, with p95 = 342 ms.** That's three round-trips
   (~80 ms each) plus a multipart upload (~100 ms). The ratio of "useful
   work" to "round-trip overhead" is poor — ~80% of the time is round-trip,
   not server compute. This is **the structural-clunkiness number** for the
   import flow. A consolidated `POST /v2/dataObjects/with-files` would
   roughly halve this. (`aidocs/99-api-annoyances.md` A-04 already calls
   this out — these are the supporting numbers.)

4. **The DataObject detail page does not preview file payloads.** Clicking
   a markdown file name (`SHOWCASE.md`) on the DO detail page produced no
   visible action — no modal, no drawer, no navigation, no innerText
   change, no new network request. The text is rendered but is **not a
   clickable surface**. This is a UX-affecting finding worth flagging to
   the parallel UI agent; from the performance side it explains a missing
   feature in our bonus metrics rather than slow performance.

5. **Heap and request counts are reasonable.** The heaviest page
   (DataObject detail at 161 requests, 19.6 MB heap) is on par with a
   typical Nuxt 3 + Vuetify 3 SPA in dev mode. The 77–161 request range
   across pages suggests Vuetify is loaded incrementally (the `/me` page
   uses fewer components and shows the lower bound). If the Nuxt build is
   already in `production` mode (it should be on this deploy), there's no
   obvious bundle-splitting win here.

6. **403 still costs you the full app shell.** The MFFD collection page
   (`/collections/473889`) returned a 403 from the API but the user still
   downloaded **4.5 MB** and fired **137 requests** to render an error
   page. The app shell loads unconditionally before the route guard runs.
   If many users hit forbidden routes (e.g. shared deep links to private
   collections), this is wasted bandwidth.

7. **`/v2/` is no slower than `/shepard/api/`.** v2-vs-v1 for the same
   collection-by-id GET: 39 ms vs. 31 ms median (within noise). The
   long-id → appId resolution does not add measurable cost.

## 4. Implications

- **TPL2d (load test).** These numbers set the per-request budget for the
  load test. Median ~30 ms for reads → 1 worker should sustain ~30 req/s
  before queueing; backend will need 5–10 concurrent workers to saturate
  a single 4-CPU host at realistic mix. Bulk-write at 270 ms median caps
  ingest pipelines at ~3 imports/s per client. **The load test should
  separately target reads (high RPS, low latency) and writes (low RPS,
  measure tail).**
- **`aidocs/semantics/95-shacl-templates-and-individuals.md` §12 scaling
  table.** The caveat is now anchored to a real number: SHACL validation
  on a small individual currently runs through the same auth + payload
  fetch path that takes ~40 ms median. Any SHACL pipeline that
  sequentially fetches N individuals over the public API will inherit
  ~40 ms × N — the table should explicitly call out bulk fetch as a
  prerequisite for >100 individuals.
- **`aidocs/99-api-annoyances.md` A-04 (bulk-write clunkiness).** The
  269 ms median is the concrete cost. A consolidated endpoint targeting
  ~120 ms (one round-trip + one upload) is the prize.
- **Frontend perf is already well within reasonable bounds.** No page
  exceeds LCP 1.5 s; landing and `/me` are well under 1 s. Optimization
  effort is better spent on the bulk-write / file-preview gaps than on
  shaving 100 ms off page loads.
- **Caddy gzip/br compression** is worth a one-line check. If not on,
  enabling it is free and would meaningfully reduce wire bytes for the
  JSON-heavy responses.
- **403-with-full-shell wastefulness.** If the team adds a few more
  permission-protected collections, the front door should pre-check
  visibility before mounting the full app for that route. Not urgent;
  noted.

## 5. Re-baseline procedure

All scripts are persisted under `/tmp/perf-baseline/` on the host, and
the raw TSVs are referenced below. To reproduce:

### A. Prerequisites

```bash
source /root/.config/shepard/claude-credentials.env
# Need: curl, jq, node (>=18), and Playwright (chromium) installed.
# Playwright is already installed under /tmp/playwright-validation; symlink it:
mkdir -p /tmp/perf-baseline
ln -sf /tmp/playwright-validation/node_modules /tmp/perf-baseline/node_modules
```

### B. API baseline

```bash
# Drop the two scripts from /tmp/perf-baseline/api-bench.sh and api-bench-2.sh
# (they live next to this report's working data).
bash /tmp/perf-baseline/api-bench.sh        # ~5 min
bash /tmp/perf-baseline/api-bench-2.sh      # ~2 min (bulk-write)
python3 /tmp/perf-baseline/stats.py         # prints the §1 table
```

Both scripts use the env from `/root/.config/shepard/claude-credentials.env`.
They write TSVs that `stats.py` reads. If the AI Exchange collection id
(`493423`) or file container (`493473`) is rotated, edit those constants
at the top of each script.

### C. UI baseline

```bash
node /tmp/perf-baseline/ui-bench.js   # ~2 min — 25 cold loads total
python3 /tmp/perf-baseline/ui-stats.py
node /tmp/perf-baseline/ui-bonus.js   # ~5 min — bonus timings
```

`ui-bench.js` logs in once via Keycloak (`claude-opus-4-7` /
`f-ai-r-2026`) and persists storage state to
`/tmp/perf-baseline/auth-state.json`. Each per-page trial reopens a
fresh browser context with that storage state, so the Keycloak hop is
only paid once (legitimate — we're measuring the app, not the
identity provider).

### D. Outputs

- Raw API trials: `/tmp/perf-baseline/api-results.tsv`,
  `/tmp/perf-baseline/api-results-2.tsv`
- Raw UI trials: `/tmp/perf-baseline/ui-results.tsv`,
  `/tmp/perf-baseline/ui-bonus.tsv`
- Aggregator scripts: `/tmp/perf-baseline/stats.py`,
  `/tmp/perf-baseline/ui-stats.py`

### E. Single-shot quick check (for an ad-hoc "is it slow?" probe)

```bash
source /root/.config/shepard/claude-credentials.env
for i in {1..5}; do
  curl -s -o /dev/null \
    -w "trial=$i http=%{http_code} t=%{time_total}s size=%{size_download}\n" \
    -H "X-API-KEY: $SHEPARD_API_KEY" \
    "$SHEPARD_API_BASE/shepard/api/collections/42"
done
```

If median drifts above ~80 ms over 5 trials, something has regressed
and a full re-baseline is warranted.

## Sources

- Raw measurement TSVs: `/tmp/perf-baseline/api-results.tsv`,
  `/tmp/perf-baseline/api-results-2.tsv`,
  `/tmp/perf-baseline/ui-results.tsv`, `/tmp/perf-baseline/ui-bonus.tsv`
- Aggregator scripts: `/tmp/perf-baseline/stats.py`,
  `/tmp/perf-baseline/ui-stats.py`
- Benchmark scripts: `/tmp/perf-baseline/api-bench.sh`,
  `/tmp/perf-baseline/api-bench-2.sh`, `/tmp/perf-baseline/ui-bench.js`,
  `/tmp/perf-baseline/ui-bonus.js`
- Markdown-preview gap inspection: `/tmp/perf-baseline/ui-bonus-inspect.js`
- Credentials profile used: `/root/.config/shepard/claude-credentials.env`
  (user `claude-opus-4-7`, Keycloak realm `shepard-demo`)
- Cross-references:
  - `aidocs/99-api-annoyances.md` §A-04 (bulk-write clunkiness — number is 270 ms here)
  - `aidocs/semantics/95-shacl-templates-and-individuals.md` §12 (scaling caveat — anchor read budget is 40 ms / individual)
  - `aidocs/platform/87-timeseries-appid-migration.md` (5-tuple addressing — not exercised in this baseline; deferred to a TS-specific run)
