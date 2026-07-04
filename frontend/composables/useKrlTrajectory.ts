/**
 * V2CONV-B5-FE — the generic "Interpret as joint trajectory" flow for a KRL
 * .src/.krl FileReference.
 *
 * The bespoke `POST /v2/krl/interpret` endpoint and its dedicated UI dissolved
 * into the generic MAPPING_RECIPE mechanism (aidocs/platform/191 decision #2,
 * the transform-direction sibling of V2CONV-B4's scene-graph dissolution). A
 * KRL interpret is now a MAPPING_RECIPE `ShepardTemplate` that binds a KRL
 * .src/.krl FileReference + a URDF FileReference + the target DataObject + the
 * TimeseriesContainer, targeting the `KrlTrajectoryShape`. Materializing it via
 * `POST /v2/mappings/{templateAppId}/materialize` mints a NEW joint-trajectory
 * TimeseriesReference and returns its appId (a REFERENCE result).
 *
 * This composable owns: building the template body, creating the template
 * (`POST /v2/templates`), and materializing it (`useMaterializeMapping`). Per
 * the CLAUDE.md rules it addresses entities by appId only, targets the /v2/
 * surface, and never asks the user for a path/URL.
 *
 * Backlog: V2CONV-B5. Design: aidocs/platform/191 §decision-2.
 */

import { materializeMapping, type MaterializeResponse } from "~/composables/useMaterializeMapping";

/** Dispatch-key IRI of the KrlTrajectoryShape (matches the in-tree executor). */
export const KRL_TRAJECTORY_SHAPE_IRI =
  "http://semantics.dlr.de/shepard/transform#KrlTrajectoryShape";

/**
 * Predicate stamped on the KRL .src/.krl FileReference pointing at the
 * MAPPING_RECIPE template appId, so re-binding the same program is idempotent.
 */
export const KRL_TEMPLATE_PREDICATE =
  "urn:shepard:mapping:krl-trajectory-template-appId";

/** `.src` (KUKA Robot Language) / `.krl` source-file extension test. Case-insensitive. */
export function isKrlSrcFile(name: string | undefined | null): boolean {
  if (!name) return false;
  const lower = name.toLowerCase();
  return lower.endsWith(".src") || lower.endsWith(".krl");
}

export interface KrlTrajectoryTemplateOpts {
  srcFileReferenceAppId: string;
  urdfFileReferenceAppId: string;
  targetDataObjectAppId: string;
  timeseriesContainerAppId: string;
  datFileReferenceAppIds?: string[] | null;
}

/**
 * Build the MAPPING_RECIPE template body for a KRL trajectory recipe. Pure +
 * exported so the unit test asserts the exact body without a network round-trip.
 */
export function buildKrlTrajectoryTemplateBody(
  opts: KrlTrajectoryTemplateOpts,
): Record<string, unknown> {
  const body: Record<string, unknown> = {
    templateKind: "MAPPING_RECIPE",
    mappingRecipeShape: KRL_TRAJECTORY_SHAPE_IRI,
    srcFileReferenceAppId: opts.srcFileReferenceAppId,
    urdfFileReferenceAppId: opts.urdfFileReferenceAppId,
    targetDataObjectAppId: opts.targetDataObjectAppId,
    timeseriesContainerAppId: opts.timeseriesContainerAppId,
  };
  if (opts.datFileReferenceAppIds && opts.datFileReferenceAppIds.length > 0) {
    body.datFileReferenceAppIds = JSON.stringify(opts.datFileReferenceAppIds);
  }
  return body;
}

/**
 * Default trajectory-recipe name from a FileReference name. Strips a trailing
 * `.src`/`.krl`; falls back to a generic label. Pure + exported for tests.
 */
export function defaultTrajectoryNameFor(
  fileReferenceName: string | null | undefined,
): string {
  const trimmed = (fileReferenceName ?? "").trim();
  if (!trimmed) return "KRL trajectory";
  const stem = trimmed.replace(/\.(src|krl)$/i, "");
  return stem ? `${stem} — trajectory` : "KRL trajectory";
}

/**
 * Locate an already-created KRL-trajectory template appId from a FileReference's
 * annotations. Returns the value of the `urn:shepard:mapping:krl-trajectory-
 * template-appId` annotation, or null.
 */
export function findKrlTrajectoryTemplateAppId(
  annotations:
    | readonly { propertyIRI?: string | null; valueName?: string | null }[]
    | null
    | undefined,
): string | null {
  const ann = annotations ?? [];
  for (const a of ann) {
    if (a?.propertyIRI === KRL_TEMPLATE_PREDICATE) {
      const v = (a?.valueName ?? "").trim();
      if (v) return v;
    }
  }
  return null;
}

// Re-derive the v2 base URL — same approach as useSceneGraphPlay / useMaterializeMapping.
function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export interface CreateKrlTrajectoryRequest extends KrlTrajectoryTemplateOpts {
  name: string;
  description?: string | null;
}

export type CreateKrlTrajectoryResult =
  | { ok: true; templateAppId: string }
  | { ok: false; status: number; detail: string };

export type MaterializeKrlResult =
  | { ok: true; derivedReferenceAppId: string; response: MaterializeResponse }
  | { ok: false; status: number; detail: string };

/**
 * Create + materialize a KRL-trajectory MAPPING_RECIPE. The two steps are split
 * so a caller can create the template once (idempotent back-annotation) and
 * re-materialize on demand.
 */
export function useKrlTrajectory() {
  const loading = ref(false);

  async function createTemplate(
    req: CreateKrlTrajectoryRequest,
  ): Promise<CreateKrlTrajectoryResult> {
    loading.value = true;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      if (!accessToken) {
        return { ok: false, status: 401, detail: "Sign in expired — refresh the page." };
      }
      const body = buildKrlTrajectoryTemplateBody(req);
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

  /**
   * Materialize an existing KRL-trajectory template. Binds the .src + URDF
   * appIds through the materialize endpoint and returns the derived
   * TimeseriesReference appId.
   */
  async function materialize(
    templateAppId: string,
    srcFileReferenceAppId: string,
    urdfFileReferenceAppId: string,
  ): Promise<MaterializeKrlResult> {
    loading.value = true;
    try {
      const response = await materializeMapping(templateAppId, {
        srcFileAppId: srcFileReferenceAppId,
        urdfFileAppId: urdfFileReferenceAppId,
      });
      const derived = response.derivedReferenceAppId;
      if (response.outputKind !== "REFERENCE" || !derived) {
        return {
          ok: false,
          status: 0,
          detail: `Materialize returned ${response.outputKind} without a derived reference appId.`,
        };
      }
      return { ok: true, derivedReferenceAppId: derived, response };
    } catch (e) {
      const status =
        typeof e === "object" && e !== null && "statusCode" in e
          ? Number((e as { statusCode?: number }).statusCode) || 0
          : 0;
      const detail = e instanceof Error ? e.message : "Network error reaching the materialize endpoint.";
      return { ok: false, status, detail };
    } finally {
      loading.value = false;
    }
  }

  return { loading, createTemplate, materialize };
}
