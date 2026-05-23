---
title: hdf5 — Quickstart
stage: deployed
last-stage-change: 2026-05-23
audience: plugin-author
synthetic_batch: true
generation_rule: feedback_no_synthetic_provenance.md
---

> 🤖 **BACKFILL — created retroactively 2026-05-23 by Claude Opus 4.7**
> per the docs-gap audit at `aidocs/agent-findings/plugin-docs-gap-audit-2026-05-23.md`.
> The plugin's behaviour is documented from the source code as it stood
> at commit `8bdc8c6163ee4ea88acde244a1c7e9672ab593a3`. If anything is
> inaccurate, the source is authoritative; please open a PR or issue.

# hdf5 — quickstart

**Goal:** create an HDF5 container in shepard, write a dataset to
it via `h5pyd`, then download the file locally and inspect it with
`h5py`.

Time: 5 minutes. Assumes the [`install.md`](install.md) HSDS
sidecar is up and credentials are set.

---

## Step 1 — create a container

```bash
SHEPARD_URL=https://shepard-api.nuclide.systems
SHEPARD_API_KEY=...

RESP=$(curl -s -X POST \
  -H "X-API-KEY: $SHEPARD_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "TR-004-hotfire",
    "description": "Hot-fire run 2026-05-12 — accelerometer + thermal data",
    "attributes": {
      "campaign": "LUMEN-2026-Q2",
      "instrument": "thrust-bench"
    }
  }' \
  "$SHEPARD_URL/v2/hdf-containers")

APPID=$(echo "$RESP" | jq -r '.appId')
HSDS_DOMAIN=$(echo "$RESP" | jq -r '.hsdsDomain')
echo "Container: $APPID"
echo "HSDS domain: $HSDS_DOMAIN"
```

Expected: `appId` is a UUID v7, `hsdsDomain` is
`/shepard/<appId>/`.

---

## Step 2 — write a dataset with `h5pyd`

Install the client:

```bash
pip install h5pyd
```

For development (HSDS reachable on `localhost:5101`):

```python
import h5pyd
import numpy as np

HSDS_ENDPOINT = "http://localhost:5101"
HSDS_USERNAME = "admin"
HSDS_PASSWORD = "admin"
DOMAIN = "/shepard/<appId>/"   # from step 1

with h5pyd.File(
    DOMAIN, mode="a",
    endpoint=HSDS_ENDPOINT,
    username=HSDS_USERNAME,
    password=HSDS_PASSWORD,
) as f:
    # Create a chunked dataset for accelerometer channels.
    grp = f.create_group("accel")
    ds = grp.create_dataset(
        "channel_0",
        shape=(100_000,),
        dtype="float32",
        chunks=(10_000,),
    )
    ds[:] = np.random.normal(0, 1.0, 100_000).astype("float32")
    ds.attrs["units"] = "g"
    ds.attrs["sample_rate_hz"] = 50_000.0
    print("Wrote 100k samples.")
```

In production, the HSDS endpoint is internal and reached only via
the backend — Phase 1 doesn't relay per-user creds. For dev,
HSDS admin creds work directly.

---

## Step 3 — download the byte-identical HDF5 file

The A5d offline-download path returns the file as a single byte
stream — open it locally with `h5py` (the non-server client):

```bash
curl -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/v2/hdf-containers/$APPID/file" \
  -o tr-004-hotfire.h5
```

Range requests are supported and forwarded to HSDS:

```bash
# Read just the file header (bytes 0–1023).
curl -H "X-API-KEY: $SHEPARD_API_KEY" \
  -H "Range: bytes=0-1023" \
  "$SHEPARD_URL/v2/hdf-containers/$APPID/file" \
  -o header.bin
```

The full-file download has `Content-Type: application/x-hdf5` and
a `Content-Disposition` filename derived from the container's
`name`.

---

## Step 4 — open it locally with `h5py`

```python
import h5py

with h5py.File("tr-004-hotfire.h5", "r") as f:
    print("Groups:", list(f.keys()))
    print("Channels:", list(f["accel"].keys()))
    data = f["accel/channel_0"][:1000]
    print("First 1000 samples:", data[:5], "...")
    print("Units:", f["accel/channel_0"].attrs["units"])
```

The byte-identical download means you can drop the file into any
HDF5-aware tool (MATLAB, Paraview, h5utils, …) without an HSDS
client.

---

## Step 5 — share with collaborators

The container inherits permissions from the user who created it.
Add a reader through the standard shepard permission surface — see
[`docs/reference/permissions.md`](../../../docs/reference/permissions.md)
for the REST shape. shepard's permission bridge (A5b) flows the
change to HSDS automatically; the collaborator's `h5pyd` calls
work with their own credentials once OIDC relay ships (A5e).

For Phase 1, collaborators can read the byte-identical file
through shepard's REST surface (`GET .../file`) without ever
touching HSDS directly.

---

## Going further

- **Multi-channel acquisition**: chunk by time-slice for fast
  range queries. HSDS reads only the chunks the query touches.
- **Compression**: pass `compression="gzip", compression_opts=4`
  to `create_dataset` — chunk-level gzip works through HSDS
  transparently.
- **Per-DataObject anchor (A5c)**: queued — once shipped, you'll
  be able to attach an `HdfReference` to a DataObject pointing
  at a specific dataset path inside the container.
- **Annotation hookup**: track in aidocs/16 `A5c-annotate`.

---

## See also

- [`reference.md`](reference.md) — full payload kind, REST surface,
  permission bridge.
- [`install.md`](install.md) — HSDS sidecar + config.
- [`h5pyd` upstream](https://github.com/HDFGroup/h5pyd).
- [`h5py` upstream](https://docs.h5py.org/).
- `aidocs/35-hdf5-hsds-implementation-design.md` — full A5 design.
