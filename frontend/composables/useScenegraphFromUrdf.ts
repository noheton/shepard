/**
 * SCENEGRAPH-CREATE-FROM-URDF-2-FE — typed wrapper around
 * `POST /v2/scene-graphs/from-urdf/{fileReferenceAppId}`.
 *
 * One-call scene mint from a URDF singleton FileReference. The backend
 * SCENEGRAPH-CREATE-FROM-URDF-1 shipped 2026-05-30 (commit `fc52785f3`)
 * and lives in its own resource class to keep `SceneGraphRest` lean.
 *
 * Why a discriminated result (vs the existing `useSceneGraph` error
 * shape): the 409 body carries `existingSceneAppId` — the entire reason
 * this UI exists is to jump straight to the existing scene when one is
 * already minted. Folding that into a `{ok:false,status:409,...}` shape
 * would force a `JSON.parse(error.detail)` at the call site. A typed
 * result keeps the routing decision pure.
 */
import type { SceneGraphIO } from "./useSceneGraph";

/** Discriminated result of {@link createFromUrdf}. */
export type CreateFromUrdfResult =
  | { ok: true; scene: SceneGraphIO }
  | { ok: false; status: number; detail: string; existingSceneAppId?: string };

export interface CreateFromUrdfRequest {
  fileReferenceAppId: string;
  name?: string | null;
  description?: string | null;
  aiAgent?: string | null;
}

// Re-derive the v2 base URL — same approach as useSceneGraph.
function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

/**
 * Default "scene name" suggestion derived from the FileReference name.
 *
 * Strips a trailing `.urdf` / `.URDF` extension; trims; falls back to a
 * generic "Scene" when the input is empty. Reading `<robot name="">`
 * from the URDF body would be richer but blows the LoC budget — see
 * SCENEGRAPH-CREATE-FROM-URDF-3-FE for the deferred enrichment.
 */
export function defaultSceneNameFor(fileReferenceName: string | null | undefined): string {
  const trimmed = (fileReferenceName ?? "").trim();
  if (!trimmed) return "Scene";
  return trimmed.replace(/\.urdf$/i, "") || "Scene";
}

/**
 * Decide what the UI should do given a {@link CreateFromUrdfResult}.
 *
 * Returns a discriminated decision so the component switches on `kind`
 * without re-deriving routes or messages. Pure — unit-tested without
 * mounting Vue.
 */
export type CreateDecision =
  | { kind: "navigate"; path: string }
  | { kind: "error"; message: string }
  | { kind: "permission"; message: string }
  | { kind: "retry"; message: string };

export function decideAfterCreate(
  result: CreateFromUrdfResult,
): CreateDecision {
  if (result.ok) {
    return { kind: "navigate", path: `/scene-graphs/${encodeURIComponent(result.scene.appId)}` };
  }
  if (result.status === 409 && result.existingSceneAppId) {
    return {
      kind: "navigate",
      path: `/scene-graphs/${encodeURIComponent(result.existingSceneAppId)}`,
    };
  }
  if (result.status === 403) {
    return {
      kind: "permission",
      message: "You need Write permission on the parent Collection to mint a scene.",
    };
  }
  if (result.status === 400) {
    return {
      kind: "error",
      message: result.detail || "URDF could not be parsed — check the file's <robot> root.",
    };
  }
  if (result.status === 0) {
    return {
      kind: "retry",
      message: result.detail || "Network error — check the backend is reachable.",
    };
  }
  return {
    kind: "error",
    message: result.detail || `Unexpected error (HTTP ${result.status}).`,
  };
}

/**
 * POST `/v2/scene-graphs/from-urdf/{fileReferenceAppId}` and return a
 * typed discriminated result. Network failures surface as `status: 0`.
 */
export function useScenegraphFromUrdf() {
  const loading = ref(false);

  async function createFromUrdf(
    req: CreateFromUrdfRequest,
  ): Promise<CreateFromUrdfResult> {
    loading.value = true;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      if (!accessToken) {
        return { ok: false, status: 401, detail: "No access token available." };
      }
      const headers: Record<string, string> = {
        Authorization: `Bearer ${accessToken}`,
        Accept: "application/json",
        "Content-Type": "application/json",
      };
      if (req.aiAgent) headers["X-AI-Agent"] = req.aiAgent;
      const body = JSON.stringify({
        name: req.name ?? null,
        description: req.description ?? null,
      });
      const url = `${v2BaseUrl()}/v2/scene-graphs/from-urdf/${encodeURIComponent(
        req.fileReferenceAppId,
      )}`;
      const response = await fetch(url, { method: "POST", headers, body });
      let parsed: unknown = null;
      try {
        parsed = await response.json();
      } catch {
        parsed = null;
      }
      if (response.ok) {
        return { ok: true, scene: parsed as SceneGraphIO };
      }
      const errBody = (parsed ?? {}) as { detail?: string; existingSceneAppId?: string };
      return {
        ok: false,
        status: response.status,
        detail: errBody.detail ?? "",
        existingSceneAppId: errBody.existingSceneAppId,
      };
    } catch (e) {
      const detail = e instanceof Error ? e.message : "Network error";
      return { ok: false, status: 0, detail };
    } finally {
      loading.value = false;
    }
  }

  return { loading, createFromUrdf };
}
