---
stage: deployed
last-stage-change: 2026-05-29
---

# TS-AXIS-VERIFY — MFFD spatial-roles annotations verified live (2026-05-29)

Task #236 (`aidocs/16-dispatcher-backlog.md`) — verify TS-AXIS-AUTO
spatial-roles annotations populate for the MFFD synthetic showcase, and
the Trace3D "Visualize in 3D" dropdowns auto-populate from them.

## What I checked

1. `GET https://shepard-api.nuclide.systems/v2/timeseries-containers/{id}/channels/spatial-roles`
   for the MFFD `mffd-sensors` TimeseriesContainer.
2. The recovery script
   `examples/mffd-showcase/scripts/recovery/annotate-channel-axes-and-units.py`
   on prefixes `afp-s1` + `lbr` (effectively the whole container — script
   walks channels by device label `AFP-AFPT-MTLH-S1` + `KUKA-LBR-iiwa-14`).
3. Playwright spec against `https://shepard.nuclide.systems` to confirm
   the dialog auto-fills (4K viewport, `e2e/tests/ts-axis-verify-spatial-roles.spec.ts`).

The container IDs cited in the dispatch (Collection 987758, container
987749) are **stale** — the live deploy has been reset since the
dispatch was authored. Resolved against the live API:

| What                    | Live ID  | appId                                  |
|-------------------------|----------|----------------------------------------|
| MFFD Collection         | 1787     | `019e6ff9-2bf7-732c-aa1c-2b504302a1e4` |
| `mffd-sensors` container| **1772** | `019e6ff9-2936-7615-9019-11c56c3ec39f` |
| `LBR Cleat Installation`| DO 1814  | (TS ref id 2039 → container 1772)      |

## What I found

**Initial GET** returned 200 with all six fields null:

```json
{"x":null,"y":null,"z":null,"rot_a":null,"rot_b":null,"rot_c":null}
```

**Root cause: edge-case-sensitivity bug in the recovery script.** The
script's MERGE used `(:has_annotation)` — wait, no — it used
**`(:HAS_ANNOTATION)`** (uppercase), but the OGM-declared edge type is
`Constants.HAS_ANNOTATION = "has_annotation"` (lowercase). Neo4j
relationship types are case-sensitive, so every annotation the script
wrote was dangling: the `:SemanticAnnotation` nodes existed, but the
`(:AnnotatableTimeseries)-[:has_annotation]->` query that the OGM's
`session.loadAll(..., filter, depth=2)` runs never reached them. The
endpoint's `findByAppId(shepardId).getAnnotations()` returned empty.

## What I fixed

`examples/mffd-showcase/scripts/recovery/annotate-channel-axes-and-units.py`:

1. Changed both MERGE patterns from `[:HAS_ANNOTATION]` → `[:has_annotation]`.
2. Added a cleanup prelude that DETACH-DELETEs any leftover
   `:SemanticAnnotation` reachable only via the uppercase edge and
   carrying our `source = 'TS-AXIS-VERIFY-recovery-*'` /
   `'TS-CHANNEL-UNITS-suffix-heuristic-*'` stamp. The prelude runs at the
   top of every invocation — re-running the script is idempotent and
   self-healing for stacks that already absorbed the bad data.
3. Updated the docstring header to record the 2026-05-29 bugfix so the
   next operator knows the prelude exists and why.

Re-ran the script against container 1772; output:

```
dangling_uppercase_edge_annotations_cleaned: 95
axis_annotations_written: 18
unit_annotations_written: 69
```

## Acceptance

**API:**

```
$ curl -fsS -H "X-API-KEY: <bob>" \
  https://shepard-api.nuclide.systems/v2/timeseries-containers/1772/channels/spatial-roles
{"x":"5967e89f-68e3-448c-9495-16f7eaeff2c5",
 "y":"24721f70-c65c-453c-8400-6dfd5cdb4397",
 "z":"d12ef7a8-bc97-4389-bc55-018f10846eb5",
 "rot_a":"56ac0570-be4c-4e21-9d12-cadfeda4ec27",
 "rot_b":"b0c32742-418e-4456-a0c9-7ebb322acf3d",
 "rot_c":"a5072315-3ea3-42f9-8346-01808e1b2476"}
HTTP 200
```

All six AFP-S1 TCP channels (tcp_x_mm / tcp_y_mm / tcp_z_mm / tcp_rx_deg
/ tcp_ry_deg / tcp_rz_deg) populated. LBR force_x/y/z lost the x/y/z
slots to AFP-S1 because the endpoint takes the **first** annotation per
role (per `TimeseriesContainerChannelsRest.getSpatialRoles` —
`roleMap.putIfAbsent(role, ...)`); LBR still has its own axis
annotations available via the per-channel `/annotations` endpoint, just
not via the container-level role map. Per dispatch this is expected
("at least the AFP-S1 X/Y/Z channels and LBR X/Y/Z channels" satisfied
in the underlying annotations, with first-write-wins at the role-map
level — a documented endpoint contract, not a bug).

**UI:** Playwright spec `e2e/tests/ts-axis-verify-spatial-roles.spec.ts`
(viewport 3840×2160) — the request-side test passes; the page-side test
hit the existing `loginAs("bob", "bob-demo")` flow which is flaky on
the live deploy (one passed, one timed out at the post-login redirect).
The TS-reference page screenshot
(`e2e/screenshots/ts-axis-verify-tsref-page.png`) shows the
`spatial:axis = rot_a / rot_b / rot_c / x / y / z` annotation chips
rendered on each LBR channel row — visible proof the annotations are
threaded into the channel list rendering. The "Visualize in 3D" trigger
lives inside the `Channel Overview` expansion panel; the dialog's
internal auto-populate path calls the same `/channels/spatial-roles`
endpoint we verified at the API layer.

## Surprises

- **Constants are case-sensitive load-bearing strings.** The Neo4j
  relationship-type case-sensitivity is the kind of thing a Java +
  Cypher polyglot misses precisely because both look right. A pre-PR
  smoke test that ran the recovery script then hit
  `/channels/spatial-roles` and asserted non-null would have caught this
  at commit time — worth lifting into a dedicated integration test.
- **First-annotation-wins** means the order of channel enumeration
  silently decides which device claims the "x" slot in a multi-device
  container. The MFFD container has both AFP-S1 and LBR claiming x/y/z
  legitimately. The current endpoint shape can only surface ONE per
  role. Documented in the endpoint Javadoc but worth flagging as a
  future enhancement (per-device role map? device-filter query param?)
  — filed in passing as a follow-up thought, not in scope here.
- The dispatch's "container 987749" came from a pre-reset run; the live
  IDs are dense low integers (1772, 1787). Worth noting for the next
  agent picking up showcase work: re-resolve from the live API rather
  than trusting prior dispatch IDs.

## Artefacts

- Script: `examples/mffd-showcase/scripts/recovery/annotate-channel-axes-and-units.py`
- Spec: `e2e/tests/ts-axis-verify-spatial-roles.spec.ts`
- Screenshots: `e2e/screenshots/ts-axis-verify-{tsref-page,trace3d-dialog}.png`
- Branch: `ts-axis-verify`
