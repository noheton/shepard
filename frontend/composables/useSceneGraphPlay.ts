/**
 * V2CONV-B4-FE — the generic "Open in 3D view" flow for a URDF FileReference.
 *
 * The bespoke `/v2/scene-graphs/*` namespace and its stored frames/joints graph
 * dissolved into the generic MAPPING_RECIPE mechanism (aidocs/platform/191
 * decision #2). A "scene-graph" is now a MAPPING_RECIPE `ShepardTemplate` that
 * binds a URDF FileReference (the kinematic tree, parsed on demand) + an
 * optional joint TimeseriesReference + a channel->joint binding map, targeting
 * the `SceneGraphPlayShape`. Materializing it through
 * `POST /v2/mappings/{templateAppId}/materialize` yields the play envelope the
 * Trace3D-family renderer plays back.
 *
 * This composable owns the create-or-reuse of that template from a URDF
 * FileReference detail page and the route to the play page. Per the CLAUDE.md
 * rules it addresses entities by appId only, targets the /v2/ surface, and
 * never asks the user for a path/URL.
 *
 * Backlog: V2CONV-B4. Design: aidocs/platform/191 §decision-2.
 */

/** Dispatch-key IRI of the SceneGraphPlayShape (matches the plugin executor). */
export const SCENE_GRAPH_PLAY_SHAPE_IRI =
  "http://semantics.dlr.de/shepard/transform#SceneGraphPlayShape";

/**
 * Predicate stamped on the URDF FileReference pointing at the MAPPING_RECIPE
 * template appId, so re-binding the same URDF is idempotent. Replaces the
 * deleted `urn:shepard:scenegraph:scene-appId` back-annotation.
 */
export const MAPPING_TEMPLATE_PREDICATE =
  "urn:shepard:mapping:scenegraph-template-appId";

export interface SceneGraphPlayBinding {
  joint: string;
  channelSelector: string;
}

/**
 * Build the MAPPING_RECIPE template body for a scene-graph play recipe. Pure +
 * exported so the unit test asserts the exact body without a network round-trip.
 */
export function buildSceneGraphPlayTemplateBody(opts: {
  urdfFileReferenceAppId: string;
  jointTimeseriesReferenceAppId?: string | null;
  jointChannelBindings?: SceneGraphPlayBinding[] | null;
}): Record<string, unknown> {
  const body: Record<string, unknown> = {
    templateKind: "MAPPING_RECIPE",
    mappingRecipeShape: SCENE_GRAPH_PLAY_SHAPE_IRI,
    urdfFileReferenceAppId: opts.urdfFileReferenceAppId,
  };
  if (opts.jointTimeseriesReferenceAppId) {
    body.jointTimeseriesReferenceAppId = opts.jointTimeseriesReferenceAppId;
  }
  if (opts.jointChannelBindings && opts.jointChannelBindings.length > 0) {
    body.jointChannelBindings = JSON.stringify(opts.jointChannelBindings);
  }
  return body;
}

/**
 * Default 3D-view name suggestion from a FileReference name. Strips a trailing
 * `.urdf`/`.rdk`; falls back to a generic label. Pure + exported for tests.
 */
export function default3dViewNameFor(
  fileReferenceName: string | null | undefined,
): string {
  const trimmed = (fileReferenceName ?? "").trim();
  if (!trimmed) return "3D view";
  return trimmed.replace(/\.(urdf|rdk)$/i, "") || "3D view";
}

/** Route to the play page for a MAPPING_RECIPE template appId. Pure builder. */
export function sceneGraphPlayRouteFor(templateAppId: string): string {
  return `/scene-graphs/play/${encodeURIComponent(templateAppId)}`;
}

/**
 * Read a template's `templateKind` via `GET /v2/templates/{appId}`. Returns the
 * kind string (e.g. "MAPPING_RECIPE", "VIEW_RECIPE") or null when the template
 * can't be read. Used by the play page to branch: only MAPPING_RECIPE templates
 * materialize into a scene-graph; a VIEW_RECIPE appId reaching the play route
 * (e.g. a collection "hero view" pointing at a render-recipe) must hand off to
 * the /shapes/render playground instead of failing the materialize call.
 * Backlog: SCENEGRAPH-PLAY-VIEWKIND-BRANCH (aidocs/16 §3962).
 */
export async function fetchTemplateKind(templateAppId: string): Promise<string | null> {
  if (!templateAppId) return null;
  try {
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    const url = `${v2BaseUrl()}/v2/templates/${encodeURIComponent(templateAppId)}`;
    const response = await fetch(url, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
        Accept: "application/json",
      },
    });
    if (!response.ok) return null;
    const tpl = (await response.json()) as { templateKind?: string };
    return tpl.templateKind ?? null;
  } catch {
    return null;
  }
}

/**
 * Locate an already-created scene-graph-play template appId from a
 * FileReference's annotations. Returns the literal value of the
 * `urn:shepard:mapping:scenegraph-template-appId` annotation, or null.
 */
export function findSceneGraphTemplateAppId(
  annotations:
    | readonly { propertyIRI?: string | null; valueName?: string | null }[]
    | null
    | undefined,
): string | null {
  const ann = annotations ?? [];
  for (const a of ann) {
    if (a?.propertyIRI === MAPPING_TEMPLATE_PREDICATE) {
      const v = (a?.valueName ?? "").trim();
      if (v) return v;
    }
  }
  return null;
}

// Re-derive the v2 base URL — same approach as the other URDF composables.
function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export interface CreateSceneGraphTemplateRequest {
  name: string;
  description?: string | null;
  urdfFileReferenceAppId: string;
  jointTimeseriesReferenceAppId?: string | null;
  jointChannelBindings?: SceneGraphPlayBinding[] | null;
}

export type CreateSceneGraphTemplateResult =
  | { ok: true; templateAppId: string }
  | { ok: false; status: number; detail: string };

/**
 * Create a MAPPING_RECIPE template (targeting SceneGraphPlayShape) from a URDF
 * FileReference. POSTs to `/v2/templates`; returns the new template appId. The
 * play page then materializes it on demand.
 */
export function useSceneGraphPlay() {
  const loading = ref(false);

  async function createTemplate(
    req: CreateSceneGraphTemplateRequest,
  ): Promise<CreateSceneGraphTemplateResult> {
    loading.value = true;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      if (!accessToken) {
        return { ok: false, status: 401, detail: "Sign in expired — refresh the page." };
      }
      const body = buildSceneGraphPlayTemplateBody({
        urdfFileReferenceAppId: req.urdfFileReferenceAppId,
        jointTimeseriesReferenceAppId: req.jointTimeseriesReferenceAppId,
        jointChannelBindings: req.jointChannelBindings,
      });
      const response = await fetch(`${v2BaseUrl()}/v2/templates`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          name: req.name,
          templateKind: "MAPPING_RECIPE",
          description: req.description ?? null,
          body: JSON.stringify(body),
        }),
      });
      if (!response.ok) {
        const text = await response.text().catch(() => "");
        return { ok: false, status: response.status, detail: text || `HTTP ${response.status}` };
      }
      const created = (await response.json()) as { appId?: string };
      if (!created.appId) {
        return { ok: false, status: 0, detail: "Template created but no appId returned." };
      }
      return { ok: true, templateAppId: created.appId };
    } catch (e) {
      return { ok: false, status: 0, detail: e instanceof Error ? e.message : "Network error." };
    } finally {
      loading.value = false;
    }
  }

  return { loading, createTemplate };
}

// ── Slice 2: patch existing template bindings ─────────────────────────────────

/**
 * Build the patched MAPPING_RECIPE body from a channel-map keyed by joint name.
 * Pure + exported so unit tests can assert the body shape without network I/O.
 *
 * Backlog: SCENEGRAPH-CANVAS-ANIM-1 slice 2.
 */
export function buildPatchedSceneGraphBody(
  urdfFileReferenceAppId: string,
  jointTimeseriesReferenceAppId: string | null | undefined,
  channelMap: Record<string, string | null>,
  joints: { name: string }[],
): Record<string, unknown> {
  const bindings = joints
    .filter(j => !!(channelMap[j.name] ?? "").trim())
    .map(j => ({ joint: j.name, channelSelector: (channelMap[j.name] as string).trim() }));
  return buildSceneGraphPlayTemplateBody({
    urdfFileReferenceAppId,
    jointTimeseriesReferenceAppId: jointTimeseriesReferenceAppId || null,
    jointChannelBindings: bindings.length > 0 ? bindings : null,
  });
}

export interface PatchSceneGraphBindingsRequest {
  templateAppId: string;
  urdfFileReferenceAppId: string;
  jointTimeseriesReferenceAppId?: string | null;
  jointChannelBindings?: SceneGraphPlayBinding[] | null;
}

export type PatchSceneGraphBindingsResult =
  | { ok: true }
  | { ok: false; status: number; detail: string };

/** Patch the joint TS bindings on an existing MAPPING_RECIPE template. */
export function usePatchSceneGraphBindings() {
  const loading = ref(false);

  async function patch(
    req: PatchSceneGraphBindingsRequest,
  ): Promise<PatchSceneGraphBindingsResult> {
    loading.value = true;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      if (!accessToken) {
        return { ok: false, status: 401, detail: "Sign in expired — refresh the page." };
      }
      const newBody = buildSceneGraphPlayTemplateBody({
        urdfFileReferenceAppId: req.urdfFileReferenceAppId,
        jointTimeseriesReferenceAppId: req.jointTimeseriesReferenceAppId,
        jointChannelBindings: req.jointChannelBindings,
      });
      const url = `${v2BaseUrl()}/v2/templates/${encodeURIComponent(req.templateAppId)}`;
      const response = await fetch(url, {
        method: "PATCH",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ body: JSON.stringify(newBody) }),
      });
      if (!response.ok) {
        const text = await response.text().catch(() => "");
        return { ok: false, status: response.status, detail: text || `HTTP ${response.status}` };
      }
      return { ok: true };
    } catch (e) {
      return { ok: false, status: 0, detail: e instanceof Error ? e.message : "Network error." };
    } finally {
      loading.value = false;
    }
  }

  return { loading, patch };
}
