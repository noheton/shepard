---
title: vis-trace3d — Install
stage: feature-defined
last-stage-change: 2026-05-28
audience: admin
---

# vis-trace3d — install

This page is for operators. End-users see
[quickstart.md](./quickstart.md); plugin authors see
[reference.md](./reference.md).

---

## What this plugin needs

- shepard `>= 6.0.0-SNAPSHOT, < 7` (the version range in the manifest's
  `shepardCompatibility`).
- A frontend served from the same image that built the in-tree
  `Trace3DView.vue` component (Phase 1 — the renderer is not yet
  extracted to the plugin's JAR; that's Phase 2).
- **No** additional sidecars. The Trace3D renderer is fully
  browser-side (Three.js / Line2 fat-line shader); the plugin
  declares no `SidecarSpec` entries in this release.
- **No** database migrations. The VIEW_RECIPE template lands in
  the existing `:ShepardTemplate` table; no new schema is added.

---

## How to enable / disable

The plugin is bundled by default in the `with-plugins` Maven profile
(active when you build with the operator-facing
`mvn package` flow). At runtime, the toggle is:

```properties
# infrastructure/.env or backend/application.properties
shepard.plugins.vis-trace3d.enabled=true
```

Default: `true`. Set to `false` to keep the JAR on the classpath but
remove the `vis-trace3d` capability from `GET /v2/admin/plugins` and
suppress the SHACL shape resource from the validate endpoint's load
path.

Verify after restart:

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  https://your.shepard/v2/admin/plugins | jq '.[] | select(.id == "vis-trace3d")'
```

Expected (when enabled):

```json
{
  "id": "vis-trace3d",
  "title": "Trace3D (color-mapped 3D path / brush trace)",
  "version": "1.0.0-SNAPSHOT",
  "state": "ACTIVE",
  "licence": "Apache-2.0"
}
```

---

## What "Phase 1" means for your deployment

This release is a **capability declaration**. The Three.js renderer
(`Trace3DView.vue`) and the `POST /v2/shapes/render` dispatcher are
**already in-tree on the backend + frontend** (since commits
`643d271dc`, `70b133d28`, `f63f82624`, `6e970e659`). The plugin's
job in Phase 1 is to:

1. Surface the `vis-trace3d` capability in `GET /v2/admin/plugins`
   so admins can disable Trace3D for their instance (e.g. policy
   reasons) by flipping the runtime toggle.
2. Ship the `Trace3DViewShape` SHACL shape (classpath resource at
   `/shapes/trace-3d-view.shacl.ttl`) so VIEW_RECIPE template bodies
   targeting Trace3D can be SHACL-validated via the existing
   `POST /v2/shapes/validate` endpoint.

Phase 2 (gated on **VIS-S1**, the `ViewRecipeRenderer` SPI) will:

- Extract the four `Trace3DView*.vue` components into this plugin
  module.
- Ship a `TraceFrameResolver` implementing the SPI — returns per-frame
  `{t, x, y, z, v}` envelopes by querying TimescaleDB through the
  existing `BulkChannelDataRequestIO` path.
- Gate the `frontend/pages/shapes/render.vue` renderer dispatch
  on the plugin's enabled state.

You don't need to do anything to prepare for Phase 2; the only
change at that boundary is that disabling
`shepard.plugins.vis-trace3d.enabled` will remove the renderer
from the frontend (today it removes only the capability tile in
`GET /v2/admin/plugins`).

---

## Known interactions

| With | Behaviour |
|---|---|
| `SHEPARD_SPATIAL_DATA_ENABLED` (SPATIAL-V6-ACTIVATE) | The Trace3D renderer reads from TimescaleDB channels, not the spatial substrate. SPATIAL-V6-ACTIVATE flipping `true` is independent of this plugin. The two land together as a coherent feature wave once both are GA. |
| `shepard-plugin-spatiotemporal` | Same Postgres datasource (`quarkus.datasource."spatial".*`) but unrelated payload kind. The two plugins can be enabled or disabled independently. |
| `shepard-plugin-video` | When a Trace3DView and a VideoStreamReference render side-by-side on the same DataObject, the timeline scrubber lives in `Trace3DView` and the player's `currentTime` is synced through the existing `VIS-SYNC` event bus (see `aidocs/agent-findings/synergy-2026-05-23-trace3d-video-sync.md`). |
| `shepard-plugin-ai` | When `aiGenerated = true` on a `:Trace3DSavedAnnotation` (future), the renderer surfaces the 🤖 badge per the f(ai)²r colour-mode convention. |

---

## Removal

If you want this plugin out of your image entirely (not just
disabled):

1. Build with `-DnoPlugins` or omit the
   `shepard-plugin-vis-trace3d` dependency from your local fork's
   `backend/pom.xml with-plugins` profile.
2. Note: the in-tree `Trace3DView.vue` renderer in the frontend
   build will still be present. To remove it entirely you must
   also strip the `frontend/components/container/timeseries/Trace3D*.vue`
   files from your custom frontend build — this is a Phase-1
   limitation that disappears once Phase 2 extracts the components.

---

## Where to look when things go wrong

- **Plugin doesn't appear in `GET /v2/admin/plugins`** — the JAR isn't
  on the classpath. Verify with `unzip -l backend/target/quarkus-app/lib/main/de.dlr.shepard.plugins.shepard-plugin-vis-trace3d-*.jar | head`.
- **`POST /v2/shapes/render` returns `422 templateKind != VIEW_RECIPE`** —
  the template body's `templateKind` field isn't `VIEW_RECIPE`. Use
  `GET /v2/templates?kind=view` to discover the right templates.
- **The Trace3DView pane shows "Renderer arriving in v2"** — that's
  the Phase-1 stub for the case where the SPI hasn't resolved the
  envelope yet. Phase 2 ships the resolver.
- **SHACL validation fails on a Trace3D recipe** — the shape resource
  on the classpath is at `/shapes/trace-3d-view.shacl.ttl`. The Jena
  loader reads it via `getResourceAsStream`; if the loader is
  rejecting the shape, the `META-INF/services/de.dlr.shepard.plugin.PluginManifest`
  file may not be present in the JAR. Re-build the plugin.
