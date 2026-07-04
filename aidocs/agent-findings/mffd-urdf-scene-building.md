---
stage: feedback-implemented
last-stage-change: 2026-06-03
---

# MFFD URDF scene-building — audit + fixes

## What I found

The scene-building flow is **more complete than "unfinished" suggests** — the
foundation is real, not a stub. Working today:

- **In-context entry** (`OpenInSceneGraphButton.vue`, SCENEGRAPH-NAV-02 +
  CREATE-FROM-URDF-2-FE): the FileReference detail page already shows
  "Create scene from this URDF" → `POST /v2/scene-graphs/from-urdf/{appId}`
  → routes to the new scene. v2-only, appId-keyed, 409-idempotent. Clean.
- **Scene editor** (`/scene-graphs/[appId].vue`): tree + sticky inspector +
  joints table + add/edit/delete + URDF export. Solid.
- **Renderer stack** (`UrdfCanvas` / `UrdfView` / `UrdfAnimator` /
  `UrdfJointPanel` / `UrdfChannelPicker` + pure helpers in
  `utils/urdfAnimation.ts`, `urdfChannelPicker.ts`) — all built, all tested
  (47 Vitest), wired into the **shapes/render VIEW_RECIPE** path.

**The gap:** the renderer was wired *only* to `shapes/render?renderer=urdf&urdfUrl=…`
(the canonical no-URL-input violator CLAUDE.md calls out) and the Collection
landing band — **never to the scene detail page itself.** So a user who
minted a kr210 scene landed on a metadata editor with **no robot visible**
and no way to pose it. That is the "unfinished URDF scene building" flo flagged.

## Opportunities

- The static kr210 sample + a seeded 6-joint AFP trajectory already exist
  (`URDF-MFFD-SHOWCASE-1`). Animation-on-scene-page is a wiring job, not new code.

## What I fixed (SCENEGRAPH-CANVAS-1, shipped this PR)

- New `SceneGraphCanvasPanel.vue` mounted atop `/scene-graphs/[appId]`. It
  renders the robot two ways for max fidelity: **source-file bytes** (real STL
  meshes) when `sourceFileAppId` is present, else the reconstructed
  **`export.urdf`** (frame links) for hand-built scenes — reusing the existing
  `fetchSceneUrdfBlobUrl` helper rather than reimplementing.
- New `useUrdfReferenceBlob.ts` composable: appId → `GET /v2/files/{appId}/content`
  (auth) → blob URL. Honours "UI never asks for paths/URLs": the frontend never
  constructs a Garage/storage URL. 11 Vitest cases.
- Manual joint posing via the existing `UrdfJointPanel`.
- Gates: lint clean on changed files; typecheck clean (main checkout);
  scene/URDF Vitest suites green.

**Design choice + opposing lens:** I render the *source file* (meshes) over
`export.urdf` (stick figure) for URDF-minted scenes. The **API Scrutinizer**
lens would push back — two render sources is more surface than one. Justified:
`export.urdf` deliberately omits visual geometry (per SCENEGRAPH-REST-1), so a
single-source choice means either no kr210 meshes (fails MFFD) or no hand-built
render. Both paths reuse existing helpers; no new endpoint.

## Gaps & blockers (filed)

- **SCENEGRAPH-CANVAS-ANIM-1** — bind a timeseries channel to drive joints on
  the scene page (the MFFD trajectory playback; UrdfAnimator already exists).
- **SCENEGRAPH-CANVAS-MESH-1** — `package://` mesh resolution for multi-file
  URDF bundles fed via blob URL (the kr210 *FileReference* carries `package://`
  STL refs; the static public sample resolves, the uploaded bundle won't yet).
- Pre-existing: `shapes/render?urdfUrl=…` still violates the no-URL-input rule
  (tracked under UI-PATHS-FROM-REFERENCES) — out of this task's scope.

## What surprised me

`EnterWorktree` can't run from an already-isolated agent, and the worktree's
`frontend/` has no hoisted `node_modules`/`.nuxt` — I symlinked `.nuxt` (gitignored)
to run Vitest and validated typecheck by copying files into the main checkout.
Two pre-existing test files (`helpMarkdown`, `useFetchRecentCollections`) fail
independent of my change — `helpMarkdown` fails on `main` too.
