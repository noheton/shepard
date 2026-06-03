/**
 * V2CONV-B3-FE — composable for the MAPPING_RECIPE materialize flow.
 *
 * Calls {@code POST /v2/mappings/{templateAppId}/materialize}, binding a map of
 * input-reference appIds (role → appId) and returning the derived output (a new
 * reference appId, or a played/rendered view-model).
 *
 * Per the CLAUDE.md "UI never asks for paths/URLs" rule, the caller passes
 * reference appIds only — never paths or URLs; the backend resolves them
 * server-side. Per the frontend-v2-only rule, this addresses entities by appId
 * and targets the /v2/ surface exclusively.
 *
 * Design: aidocs/platform/191 §4. Backlog: V2CONV-B3.
 */

export type MaterializeOutputKind = "REFERENCE" | "VIEW";

export interface MaterializeResponse {
  templateAppId: string;
  outputKind: MaterializeOutputKind;
  derivedReferenceAppId?: string | null;
  viewModel?: Record<string, unknown> | null;
  executor: string;
}

/**
 * Build the request body for a materialize call from a role→appId binding map.
 * Pure + side-effect-free so it is trivially unit-testable: drops blank/empty
 * appId values (a half-filled picker row must not send an empty binding).
 */
export function buildMaterializeBody(
  inputReferenceAppIds: Record<string, string>,
): { inputReferenceAppIds: Record<string, string> } {
  const cleaned: Record<string, string> = {};
  for (const [role, appId] of Object.entries(inputReferenceAppIds ?? {})) {
    if (typeof appId === "string" && appId.trim().length > 0) {
      cleaned[role] = appId.trim();
    }
  }
  return { inputReferenceAppIds: cleaned };
}

/**
 * The materialize endpoint path for a given template appId. Centralised so the
 * /v2/ prefix lives in exactly one place.
 */
export function materializePath(templateAppId: string): string {
  return `/v2/mappings/${encodeURIComponent(templateAppId)}/materialize`;
}

/**
 * POST a materialize request. Thin wrapper over `$fetch` so the page stays
 * declarative; returns the typed response or throws on non-2xx.
 */
export async function materializeMapping(
  templateAppId: string,
  inputReferenceAppIds: Record<string, string>,
): Promise<MaterializeResponse> {
  return await $fetch<MaterializeResponse>(materializePath(templateAppId), {
    method: "POST",
    body: buildMaterializeBody(inputReferenceAppIds),
  });
}
