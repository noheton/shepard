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
 * POST a materialize request. Resolves the v2 base URL + Bearer token from
 * the same `useAuth` session every other v2 caller uses (see
 * `useSceneGraphPlay.ts`'s `fetchTemplateKind`). The earlier shape called
 * `$fetch(relativePath)` with no auth header, which 401'd against the
 * deployed backend on the scene-graph play page (J-screenshot regression,
 * 2026-06-30).
 */
export async function materializeMapping(
  templateAppId: string,
  inputReferenceAppIds: Record<string, string>,
): Promise<MaterializeResponse> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  const url = `${materializeV2BaseUrl()}${materializePath(templateAppId)}`;
  const response = await fetch(url, {
    method: "POST",
    headers: {
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify(buildMaterializeBody(inputReferenceAppIds)),
  });
  if (!response.ok) {
    const text = await response.text().catch(() => "");
    throw new Error(`[POST] "${materializePath(templateAppId)}": ${response.status}${text ? ` — ${text}` : ""}`);
  }
  return (await response.json()) as MaterializeResponse;
}

/** Same derivation as the sibling `useSceneGraphPlay.ts` v2 base URL helper. */
function materializeV2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}
