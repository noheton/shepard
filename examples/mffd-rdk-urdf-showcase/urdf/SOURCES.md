# URDF sources

The URDF + visual meshes for the **KUKA KR210 R2700/2** in this directory are
flattened (via `xacro`) from the open-source
[`kroshu/kuka_robot_descriptions`][repo] ROS 2 package. Upstream is
`master` at commit
[`f648ded13cd440ac99b03360209bc9cf3343e325`][commit] (snapshot taken
2026-05-30).

[repo]:   https://github.com/kroshu/kuka_robot_descriptions
[commit]: https://github.com/kroshu/kuka_robot_descriptions/tree/f648ded13cd440ac99b03360209bc9cf3343e325

The KR210 R2700/2 is the real MFFD cell robot (per the
[`examples/mffd-showcase/seed.py`](../../mffd-showcase/seed.py#L114) line 114
naming **"AFPT MTLH / KUKA KR270 R2700"** — the R2700 reach class; the
KR210 / KR270 distinction is payload-only, kinematics are shared by the
quantec family). The previous KR210 L150 substitution from
`ros-industrial/kuka_experimental` has been replaced.

## Licence

The entire `kroshu/kuka_robot_descriptions` repository is licensed
**Apache License 2.0** (see
<https://github.com/kroshu/kuka_robot_descriptions/blob/master/LICENSE>).
That licence is compatible with Shepard's combined Apache-2.0 / MIT licence
posture per
[`.github/dependency-review-config.yml`](../../../.github/dependency-review-config.yml).
No GPL / AGPL / SSPL components are introduced.

Per the Apache-2.0 notice clause we retain the upstream copyright notices
inside each file (the flattened URDF carries the xacro autogeneration
banner; the STL meshes carry no embedded attribution beyond the
Apache-2.0-level claim).

## Flatten procedure (xacro → URDF)

The upstream is a xacro macro file with ROS-2 substitution args
(`$(find ...)`, `${pi/2}`, `${radians(...)}`). To flatten without a ROS
installation we:

1. Read `kuka_quantec_support/urdf/kr210_r2700_2.urdf.xacro` (top file)
   and `kr210_r2700_2_macro.xacro` (link/joint macro).
2. Drop the `kr_quantec_ros2_control_macro.xacro` include + the
   `<xacro:kuka_quantec_ros2_control …>` block (ROS-2-control isn't
   needed for kinematic playback).
3. Inline the macro body into a single self-contained xacro (no
   `$(find …)`).
4. Run `xacro <inlined>.urdf.xacro > kr210_r2700_2.urdf` to evaluate the
   `${prefix}`, `${pi}`, `${radians(...)}` expressions.

The result has 6 revolute joints (joint_1 … joint_6) + 3 fixed frames
(`base`, `flange`, `tool0`) per the ROS-Industrial conventions.
ikpy parses the chain cleanly (8 links, 6 movable joints, validated
via `krl_interpreter.ik.urdf_loader.UrdfLoader.load`).

## Files

| Path                                                                 | Upstream path                                                                | Size  | sha256                                                             | Licence    |
|----------------------------------------------------------------------|------------------------------------------------------------------------------|-------|--------------------------------------------------------------------|------------|
| `kr210_r2700_2.urdf`                                                 | `kuka_quantec_support/urdf/kr210_r2700_2.urdf.xacro` (flattened)             | 8.4 KB | `6ab210d47377d8c7ce7f8e0fba04e6da9df85e8469c02ea27589bb9d9ed779de` | Apache-2.0 |

Visual meshes (binary STL) ship at
`frontend/public/urdf-samples/kr210_r2700_2/kuka_quantec_support/meshes/kr210_r2700_2/visual/`
so the browser can fetch them directly via the `urdf-loader`
`packagePath = "/urdf-samples/kr210_r2700_2"` setting (URDF refs are
`package://kuka_quantec_support/meshes/...`):

| Path under public                                                                                                       | Upstream path                                                                                      | Size   | sha256                                                             |
|-------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|--------|--------------------------------------------------------------------|
| `urdf-samples/kr210_r2700_2/kuka_quantec_support/meshes/kr210_r2700_2/visual/base_link.stl`                              | same                                                                                               | 1.9 MB | `b0f389dfdecc457c8292dad973d6c9f91eb58c5c56b1a927fdc0000550f82b0c` |
| `urdf-samples/kr210_r2700_2/kuka_quantec_support/meshes/kr210_r2700_2/visual/link_1.stl`                                 | same                                                                                               | 4.5 MB | `5587939a9e4f09125091ae998cea2acd4548ca742c3984b70d94e51e33bc5923` |
| `urdf-samples/kr210_r2700_2/kuka_quantec_support/meshes/kr210_r2700_2/visual/link_2.stl`                                 | same                                                                                               | 1.4 MB | `0f2eb22f082bb51fb6c5a80eae8104d95de7a1414f8e6098e1ae7a1260dc68f1` |
| `urdf-samples/kr210_r2700_2/kuka_quantec_support/meshes/kr210_r2700_2/visual/link_3.stl`                                 | same                                                                                               | 3.0 MB | `bced5ec150a32ad55cb9a77745118f04a35b8b8a269f243e8f646d1a43280ae5` |
| `urdf-samples/kr210_r2700_2/kuka_quantec_support/meshes/kr210_r2700_2/visual/link_4.stl`                                 | same                                                                                               | 350 KB | `e6a5d73b9139b8599d4fe1b62d4a29fa2961bef21644af1a4e6b03d79781fb3a` |
| `urdf-samples/kr210_r2700_2/kuka_quantec_support/meshes/kr210_r2700_2/visual/link_5.stl`                                 | same                                                                                               | 560 KB | `1a5211c018eb1562a8790330d12d2dd692ab6511ee8d210958bbd9c6b162aa69` |
| `urdf-samples/kr210_r2700_2/kuka_quantec_support/meshes/kr210_r2700_2/visual/link_6.stl`                                 | same                                                                                               | 81 KB  | `47704d8b30bef58e8af8c9077fb001fc898a954c8aa49b881146c30d13fd2c5e` |

Total: ~12 MB across 7 mesh files. Each is under the 5 MB ceiling
(`link_1.stl` is the largest at 4.5 MB). Collision meshes are NOT
shipped — the `urdf-loader` viewer renders only the visual meshes.

The URDF is **also** committed to this directory (`kr210_r2700_2.urdf`)
so the seeder can upload it to a Shepard FileContainer as provenance — the
runtime fetch path remains the
`frontend/public/urdf-samples/kr210_r2700_2/` static location for browser
CORS simplicity, see `SHOWCASE.md §"Caveats"`.

## Legacy KR210 L150 — left in place

The previous L150 substitute still lives at
`frontend/public/urdf-samples/kr210/` and
`examples/mffd-rdk-urdf-showcase/urdf/kr210l150.urdf`. No other code
consumes it (`grep -rln "/urdf-samples/kr210"` — only this showcase
references). It is left undisturbed for backwards compatibility with
the existing Collection 4289 seed records and for kinematic-family
comparison.

## Refresh procedure

If a future agent re-pulls these files (e.g. to track a security patch
in the upstream URDF), the procedure is:

```bash
# In a worktree:
cd /tmp && mkdir kroshu-fetch && cd kroshu-fetch
COMMIT=<new-sha>
for f in kr210_r2700_2.urdf.xacro kr210_r2700_2_macro.xacro; do
  curl -sfL -o "$f" \
    "https://raw.githubusercontent.com/kroshu/kuka_robot_descriptions/$COMMIT/kuka_quantec_support/urdf/$f"
done
# Inline the macro into the top file (drop ROS-2-control include + macro call),
# then run: xacro <inlined>.urdf.xacro > kr210_r2700_2.urdf
cp kr210_r2700_2.urdf <repo>/examples/mffd-rdk-urdf-showcase/urdf/
cp kr210_r2700_2.urdf <repo>/frontend/public/urdf-samples/kr210_r2700_2/kuka_quantec_support/urdf/

# Meshes:
MESH_DST=<repo>/frontend/public/urdf-samples/kr210_r2700_2/kuka_quantec_support/meshes/kr210_r2700_2/visual
for f in base_link link_1 link_2 link_3 link_4 link_5 link_6; do
  curl -sfL -o "$MESH_DST/${f}.stl" \
    "https://raw.githubusercontent.com/kroshu/kuka_robot_descriptions/$COMMIT/kuka_quantec_support/meshes/kr210_r2700_2/visual/${f}.stl"
done
```

Update the commit SHA in this file accordingly.
