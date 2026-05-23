---
title: spatial — Quickstart
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

# spatial — quickstart

**Goal:** upload a point observation to a `SpatialDataContainer`
and query it with a bounding-box filter.

Time: 3 minutes. Assumes the
[`install.md`](install.md) PostGIS sidecar is up and the plugin is
enabled.

---

## Step 1 — create a `SpatialDataContainer`

A spatial container lives inside a Collection. Use the standard
shepard container REST verbs:

```bash
SHEPARD_URL=https://shepard-api.nuclide.systems
SHEPARD_API_KEY=...
COLLECTION_ID=42

FC=$(curl -s -X POST \
  -H "X-API-KEY: $SHEPARD_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "lumen-bench-positions",
    "description": "Hot-fire bench accelerometer locations"
  }' \
  "$SHEPARD_URL/shepard/api/collections/$COLLECTION_ID/spatialDataContainers")

SPATIAL_ID=$(echo "$FC" | jq -r '.id')
echo "Container id: $SPATIAL_ID"
```

The response carries the standard `BasicContainer` shape — `id`,
`name`, `description`, `attributes`, `permissions`.

---

## Step 2 — upload a point

`SpatialDataPoint` carries `x`, `y`, `z` (mandatory), an optional
`timestamp` (nanoseconds since epoch), and a free-form `attributes`
map.

```bash
curl -X POST \
  -H "X-API-KEY: $SHEPARD_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "x": 11.567,
    "y": 48.085,
    "z": 510.0,
    "timestamp": 1716470000000000000,
    "attributes": {
      "sensorId": "ACC-7",
      "calibratedOn": "2026-04-01"
    }
  }' \
  "$SHEPARD_URL/shepard/api/spatialDataContainers/$SPATIAL_ID/payload"
```

The `(x, y)` pair is interpreted as EPSG:4326 (WGS-84 lat/lon
in degrees); `z` is in metres above the ellipsoid. The example
above is roughly Lampoldshausen, DLR's hot-fire test site.

---

## Step 3 — list points back

```bash
curl -s -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/shepard/api/spatialDataContainers/$SPATIAL_ID/payload" | jq .
```

Response carries the points you uploaded plus their assigned
internal IDs.

---

## Step 4 — bounding-box query

The plugin supports a handful of geometry filters:
`AxisAlignedBoundingBox`, `OrientedBoundingBox`, `BoundingSphere`,
and `KNearestNeighbor`. Bounding-box is the simplest — pass the
JSON as a query parameter (URL-encoded):

```bash
FILTER='{
  "type": "AABB",
  "min": [11.0, 48.0, 0.0],
  "max": [12.0, 49.0, 1000.0]
}'

curl -s -G -H "X-API-KEY: $SHEPARD_API_KEY" \
  --data-urlencode "geometryFilter=$FILTER" \
  "$SHEPARD_URL/shepard/api/spatialDataContainers/$SPATIAL_ID/payload"
```

Only points within the box come back. The PostGIS spatial index
makes this query O(log n) even for millions of points.

---

## Step 5 — attach the container to a DataObject

The DataObject anchor lives at `:SpatialDataReference`:

```bash
DATA_OBJECT_ID=...

curl -X POST \
  -H "X-API-KEY: $SHEPARD_API_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"bench-positions\",
    \"description\": \"Spatial layout of accelerometers\",
    \"spatialDataContainerId\": $SPATIAL_ID
  }" \
  "$SHEPARD_URL/shepard/api/collections/$COLLECTION_ID/dataObjects/$DATA_OBJECT_ID/spatialDataReferences"
```

Now the DataObject pane in the UI lists the spatial reference and
its container; opening it shows the points on a map view.

---

## Going further

- **GeoJSON ingest** (planned): a future slice will accept a
  GeoJSON `FeatureCollection` POST body so you can drag a `.geojson`
  file into the UI. Until then, point-by-point upload is the
  supported path.
- **K-Nearest-Neighbor filter**: `{"type": "KNN", "point": [x,y,z],
  "k": 10}` returns the 10 closest points.
- **Polygon overlay queries**: not yet supported — track the
  follow-up under aidocs/16 `SPATIAL-polygon-filter`.

---

## See also

- [`reference.md`](reference.md) — full payload shape + endpoint list.
- [`install.md`](install.md) — PostGIS sidecar + datasource setup.
- [GeoJSON spec (RFC 7946)](https://datatracker.ietf.org/doc/html/rfc7946).
- [PostGIS spatial-index tuning](https://postgis.net/docs/manual-3.4/using_postgis_dbmanagement.html#idx_spatial_indexes).
