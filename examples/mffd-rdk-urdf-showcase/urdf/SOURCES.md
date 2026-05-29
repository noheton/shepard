# URDF sources

The URDF + visual meshes for the **KUKA KR210 L150** in this directory are
copied from the open-source [`ros-industrial/kuka_experimental`][repo] ROS
package. Upstream is `melodic-devel` at commit
[`8d9292b04a22628b1b78d989e2ddd3abb913bf92`][commit] (snapshot taken
2026-05-29).

[repo]:   https://github.com/ros-industrial/kuka_experimental
[commit]: https://github.com/ros-industrial/kuka_experimental/tree/8d9292b04a22628b1b78d989e2ddd3abb913bf92

## Licence

The entire `kuka_experimental` repository is licensed
**Apache License 2.0** (see
<https://github.com/ros-industrial/kuka_experimental/blob/melodic-devel/LICENSE>).
That licence is compatible with Shepard's combined Apache-2.0 / MIT licence
posture per
[`.github/dependency-review-config.yml`](../../../.github/dependency-review-config.yml).
No GPL / AGPL / SSPL components are introduced.

Per the Apache-2.0 notice clause we retain the upstream copyright notices
inside each file (the URDF has none; the `.dae` Collada meshes carry
no embedded attribution beyond the Apache-2.0-level claim).

## Files

| Path                                                                 | Upstream path                                                            | Size  | Licence    |
|----------------------------------------------------------------------|--------------------------------------------------------------------------|-------|------------|
| `kr210l150.urdf`                                                     | `kuka_kr210_support/urdf/kr210l150.urdf`                                 | 7.6 KB | Apache-2.0 |

Visual meshes (Collada `.dae`) ship at
`frontend/public/urdf-samples/kr210/kuka_kr210_support/meshes/kr210l150/visual/`
so the browser can fetch them directly via the `urdf-loader`
`packagePath = "/urdf-samples/kr210"` setting (URDF refs are
`package://kuka_kr210_support/meshes/...`):

| Path under public                                                                                       | Upstream path                                                                           | Size   |
|---------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|--------|
| `urdf-samples/kr210/kuka_kr210_support/meshes/kr210l150/visual/base_link.dae`                            | same                                                                                    | 385 KB |
| `urdf-samples/kr210/kuka_kr210_support/meshes/kr210l150/visual/link_1.dae`                               | same                                                                                    | 1.6 MB |
| `urdf-samples/kr210/kuka_kr210_support/meshes/kr210l150/visual/link_2.dae`                               | same                                                                                    | 5.2 MB |
| `urdf-samples/kr210/kuka_kr210_support/meshes/kr210l150/visual/link_3.dae`                               | same                                                                                    | 1.7 MB |
| `urdf-samples/kr210/kuka_kr210_support/meshes/kr210l150/visual/link_4.dae`                               | same                                                                                    | 1.0 MB |
| `urdf-samples/kr210/kuka_kr210_support/meshes/kr210l150/visual/link_5.dae`                               | same                                                                                    | 2.0 MB |
| `urdf-samples/kr210/kuka_kr210_support/meshes/kr210l150/visual/link_6.dae`                               | same                                                                                    | 1.5 MB |

Total: ~14 MB across 7 mesh files. Each is under the 10 MB git ceiling
(`link_2.dae` is the largest at 5.2 MB). Collision meshes
(STL, ~221 KB total) are NOT shipped — the `urdf-loader` viewer renders
only the visual meshes.

The URDF is **also** committed to this directory (`kr210l150.urdf`) so the
seeder can upload it to a Shepard FileContainer as provenance — the runtime
fetch path remains the `frontend/public/urdf-samples/kr210/` static
location for browser CORS simplicity, see `SHOWCASE.md §"Caveats"`.

## Caveat — robot model substitution

The MFZ.rdk tier-1 scrape names the robot controller as `R20_MFZDriver`
(reproduced verbatim in the `RdkTextScrapeParserMFZFixtureTest` JUnit
fixture). The driver identity doesn't pin a specific robot model on its
own; the cell-level context (cross-referenced with the live MFFD AFP
showcase at
[`examples/mffd-showcase/seed.py`](../../mffd-showcase/seed.py#L114) line
114, which names the AFP robot as
**"AFPT MTLH / KUKA KR270 R2700"**) identifies the actual cell robot as a
KUKA KR270 R2700.

**Substitution made here:** the open-source
[`ros-industrial/kuka_experimental`][repo] package does NOT provide a
`kuka_kr270_support` URDF (as of 2026-05-29 the published support
packages are KR3, KR5, KR6, KR10, KR16, KR120, KR150, KR210, plus
LBR iiwa). The closest available kinematic family member is the
**KR210 L150** (also a six-axis articulated arm in the same payload
class; 210 kg payload vs. 270 kg, both 2700–3100 mm reach class). The
KR210 visual style + joint topology is representative of the real cell
robot for animator-playback purposes.

The synthetic joint trajectory in `../trajectory/` exercises the KR210
joint axes within their declared URDF limits. **For a faithful render
of the actual MFFD cell**, the KR270 URDF would need to come from one
of:

- a KUKA-supplied CAD package (proprietary; not redistributable),
- a `RdkToUrdfExporter` sidecar run against MFZ.rdk that
  re-derives the kinematics directly (Phase 2 of URDF-WEBVIEW-1; queued
  as a deferred backlog row, see `SHOWCASE.md §"Phase 2 — what's
  missing"`).

Both paths are gated on RoboDK SDK licence + KUKA OLP licence
availability; the open-source KR210 substitution is the honest interim.

## Refresh procedure

If a future agent re-pulls these files (e.g. to track a security patch
in the upstream URDF), the procedure is:

```bash
cd examples/mffd-rdk-urdf-showcase/urdf
curl -sf -o kr210l150.urdf \
    https://raw.githubusercontent.com/ros-industrial/kuka_experimental/<SHA>/kuka_kr210_support/urdf/kr210l150.urdf

cd ../../../frontend/public/urdf-samples/kr210/kuka_kr210_support/meshes/kr210l150/visual
for f in base_link link_1 link_2 link_3 link_4 link_5 link_6; do
  curl -sf -o "${f}.dae" \
    "https://raw.githubusercontent.com/ros-industrial/kuka_experimental/<SHA>/kuka_kr210_support/meshes/kr210l150/visual/${f}.dae"
done
```

Update the commit SHA in this file accordingly.
