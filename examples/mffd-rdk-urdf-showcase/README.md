# MFFD RDK → URDF Showcase — runbook

End-to-end demonstrator for Shepard's browser URDF viewer + animator
driven by real DLR MFFD AFP-cell metadata. For the user-facing narrative
and click-walkthrough see [`SHOWCASE.md`](./SHOWCASE.md). For URDF
licensing/sourcing see [`urdf/SOURCES.md`](./urdf/SOURCES.md).

## Prerequisites

- A running Shepard backend reachable from this host. Live target:
  `https://shepard-api.nuclide.systems`.
- An API key (JWT) for a user with write access to the target. The
  template-creation step also wants the `instance-admin` role — when
  absent it is skipped with a clear log line and the URDF view stays
  launchable via the URL printed at the end.
- Python 3.11+ with the bundled `shepard_client` SDK on the path.
- The MFZ.rdk source file at
  `examples/mffd-showcase/raw-data/mffd-data/cell/MFZ.rdk` (12.1 MB,
  gitignored). When missing the RDK upload step is logged + skipped;
  the URDF + trajectory steps still run.

## Run

```bash
# From repo root
cd examples/mffd-rdk-urdf-showcase

# Live deploy
python3 seed.py \
    --host https://shepard-api.nuclide.systems/shepard/api \
    --apikey "$SHEPARD_API_KEY"

# Reset + re-seed
python3 seed.py \
    --host https://shepard-api.nuclide.systems/shepard/api \
    --apikey "$SHEPARD_API_KEY" \
    --reset
```

`SHEPARD_API_KEY` is the JWT for the demo user (per
`/root/.config/shepard/claude-credentials.env` on the nuclide dev box,
or the operator-issued key). Exit code `0` on success.

The seeder is idempotent — re-running detects existing entities by
name and skips work that's already done.

## What it creates

- One Collection: `MFFD RDK → URDF Viewer Showcase` (public).
- Two shared containers:
  - `mffd-rdk-urdf-files` (FileContainer)
  - `mffd-rdk-urdf-trajectory` (TimeseriesContainer)
- Three DataObjects:
  - `MFFD AFP Cell — MFZ.rdk source` — one FileReference holding the
    real `.rdk` (RDK-PARSE-1 scrapes 8 tier-1 annotations on upload).
  - `R10 (KR210 L150) — kinematic model` — eight FileReferences (URDF
    + seven Collada visual meshes; provenance copies of the static
    `frontend/public/urdf-samples/kr210/...` set).
  - `AFP Ply 5 layup — joint trajectory` — one TimeseriesReference
    with 6 joint-angle channels (3000 points each); each channel
    annotated `urn:shepard:urdf:joint = <jointName>`.
- One `:ShepardTemplate` of kind `VIEW_RECIPE` wiring the KR210 URDF
  + joint→channel map (best-effort — requires `instance-admin`).

## Smoke-test (without writing)

```bash
python3 -c "
import requests
r = requests.get('https://shepard-api.nuclide.systems/shepard/api/version', timeout=5)
print(r.status_code, r.text[:80])
"
```

A non-200 means the backend isn't up — fix the backend first.

## Troubleshooting

| Symptom                                                 | Cause / fix                                                                                                  |
|---------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| `Bad Gateway` from search                               | Backend restarting / unhealthy. `docker logs infrastructure-backend-1 --tail 40`.                            |
| `SKIP … MFZ.rdk not found`                              | Mount `examples/mffd-showcase/raw-data/mffd-data/cell/MFZ.rdk` (12.1 MB). RDK step skips cleanly.            |
| `SKIP … ShepardTemplate (needs instance-admin)`         | The user isn't an admin. The URDF view is still launchable via the URL the seeder prints in its final block. |
| `SKIP … channel xxx field=yyy joint annotation 4xx/5xx` | The annotation endpoint shape changed. Check the FE channel-picker still resolves bindings via the heuristic. |
| URDF doesn't paint in the browser                       | Confirm `/urdf-samples/kr210/kuka_kr210_support/urdf/kr210l150.urdf` returns 200 from the frontend host.     |

## Generated artefacts

The `trajectory/*.csv` files are checked in (small; deterministic
output of `trajectory/generate.py`). To regenerate:

```bash
python3 trajectory/generate.py
```
