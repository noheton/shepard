/**
 * SCENEGRAPH-REST-1-UI — typed wrapper around `/v2/scene-graphs/{appId}`.
 *
 * The scene-graph REST endpoints (SCENEGRAPH-REST-1) live on the v2
 * development shelf and are not in `@dlr-shepard/backend-client` yet —
 * same pattern as `useKrlInterpret.ts` and `useFetchVideoStreamReferences.ts`.
 *
 * Surfaces user-friendly error messages for the documented status codes
 * (auth gating today is `@Authenticated`-only — per-scene permissions are
 * queued as SCENEGRAPH-PERMS-1):
 *  - 401 → "Sign in expired — refresh the page."
 *  - 403 → "You don't have write access on this scene."
 *  - 404 → "Scene not found."
 *  - 409 → "Conflict — the parent or child frame is missing or invalid."
 */

// ── Types — mirror backend IO shapes ──────────────────────────────────────────

export type FrameKind = "FRAME" | "JOINT" | "TOOL" | "BASE" | "TCP";

export type JointType = "REVOLUTE" | "PRISMATIC" | "FIXED" | "CONTINUOUS";

export interface FrameIO {
  appId: string;
  name?: string | null;
  parentFrameAppId?: string | null;
  x?: number | null;
  y?: number | null;
  z?: number | null;
  rx?: number | null;
  ry?: number | null;
  rz?: number | null;
  kind?: FrameKind | null;
}

export interface JointIO {
  appId: string;
  name?: string | null;
  parentFrameAppId?: string | null;
  childFrameAppId?: string | null;
  axisX?: number | null;
  axisY?: number | null;
  axisZ?: number | null;
  limitMin?: number | null;
  limitMax?: number | null;
  type?: JointType | null;
  homeAngle?: number | null;
}

export interface SceneGraphIO {
  appId: string;
  name?: string | null;
  description?: string | null;
  sourceFileAppId?: string | null;
  rootFrameAppId?: string | null;
  frames?: FrameIO[];
  joints?: JointIO[];
  // JSON-LD framing tokens (only present on application/ld+json responses).
  "@context"?: string;
  "@type"?: string;
}

export interface CreateFrameRequestIO {
  name?: string | null;
  parentFrameAppId?: string | null;
  x?: number | null;
  y?: number | null;
  z?: number | null;
  rx?: number | null;
  ry?: number | null;
  rz?: number | null;
  kind?: FrameKind | null;
}

export interface PatchFrameRequestIO {
  name?: string | null;
  parentFrameAppId?: string | null;
  x?: number | null;
  y?: number | null;
  z?: number | null;
  rx?: number | null;
  ry?: number | null;
  rz?: number | null;
  kind?: FrameKind | null;
}

export interface CreateJointRequestIO {
  name?: string | null;
  parentFrameAppId: string;
  childFrameAppId: string;
  axisX?: number | null;
  axisY?: number | null;
  axisZ?: number | null;
  limitMin?: number | null;
  limitMax?: number | null;
  type?: JointType | null;
  homeAngle?: number | null;
}

export interface SceneGraphError {
  status: number;
  message: string;
  detail: string;
}

/**
 * SCENEGRAPH-LIST-1 — list-item shape returned by `GET /v2/scene-graphs`.
 *
 * Mirrors backend {@code SceneGraphListItemIO}: trimmed scalar identity +
 * frame/joint counts for index browsing. Frame and joint arrays are returned
 * by {@link fetchScene} instead.
 */
export interface SceneListItem {
  appId: string;
  name?: string | null;
  description?: string | null;
  sourceFileAppId?: string | null;
  rootFrameAppId?: string | null;
  frameCount: number;
  jointCount: number;
  createdAt?: number | null;
  updatedAt?: number | null;
}

/**
 * SCENEGRAPH-LIST-1 — page envelope returned by `GET /v2/scene-graphs`.
 *
 * Mirrors backend {@code SceneGraphListIO}: items + total + page + size.
 */
export interface SceneListPage {
  items: SceneListItem[];
  total: number;
  page: number;
  size: number;
}

export interface ListOptions {
  page?: number;
  size?: number;
  aiAgent?: string | null;
}

// ── Pure helpers (testable in isolation) ──────────────────────────────────────

/**
 * Map an HTTP status code to a user-friendly message.
 *
 * Exported so tests + result panels render the same text.
 */
export function sceneGraphErrorMessageForStatus(
  status: number,
  fallback?: string,
): string {
  switch (status) {
    case 401:
      return "Sign in expired — refresh the page.";
    case 403:
      return "You don't have write access on this scene.";
    case 404:
      return "Scene not found.";
    case 409:
      return "Conflict — the parent or child frame is missing or invalid.";
    case 400:
      return fallback ?? "Malformed request — check the required fields.";
    case 500:
      return fallback ?? "Server error while reading the scene graph.";
    default:
      return fallback ?? `Unexpected error (HTTP ${status}).`;
  }
}

/**
 * Sanitise a scene name into a filename-safe URDF filename.
 *
 * Falls back to `scene_<short-appId>.urdf` when the name is empty.
 */
export function urdfDownloadFilename(
  sceneName: string | null | undefined,
  sceneAppId: string,
): string {
  const trimmed = (sceneName ?? "").trim();
  if (!trimmed) {
    const short = (sceneAppId ?? "scene").slice(0, 8);
    return `scene_${short}.urdf`;
  }
  // Replace whitespace + filesystem-hostile characters with underscores.
  const safe = trimmed
    .replace(/[\s\\/:*?"<>|]+/g, "_")
    .replace(/_+/g, "_")
    .replace(/^_+|_+$/g, "");
  return `${safe || "scene"}.urdf`;
}

/**
 * Build a child-index from a flat frame list (parent appId → children appIds).
 *
 * Root frames (no parent) collect under the empty-string key. Pure function —
 * extracted so tests can verify ordering + orphan handling without mounting
 * the recursive tree component.
 */
export function indexFramesByParent(
  frames: FrameIO[] | undefined | null,
): Map<string, FrameIO[]> {
  const map = new Map<string, FrameIO[]>();
  if (!frames) return map;
  for (const f of frames) {
    const key = f.parentFrameAppId ?? "";
    const bucket = map.get(key);
    if (bucket) bucket.push(f);
    else map.set(key, [f]);
  }
  return map;
}

/**
 * Count descendants of `frameAppId` in the parent→children index (recursive).
 *
 * Used by the delete-confirm dialog to surface "this will delete N frames".
 */
export function countDescendants(
  frameAppId: string,
  byParent: Map<string, FrameIO[]>,
): number {
  let count = 0;
  const stack = [frameAppId];
  while (stack.length > 0) {
    const cur = stack.pop()!;
    const kids = byParent.get(cur) ?? [];
    for (const kid of kids) {
      count += 1;
      stack.push(kid.appId);
    }
  }
  return count;
}

// ── Runtime config helper ─────────────────────────────────────────────────────

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

// ── Composable ────────────────────────────────────────────────────────────────

interface RequestOptions {
  aiAgent?: string | null;
  acceptJsonLd?: boolean;
}

/**
 * Typed wrapper around the SCENEGRAPH-REST-1 surface. Returns a tidy
 * collection of bound functions; each one resolves to the parsed body
 * on 2xx, or sets `error.value` and resolves to `null` on 4xx/5xx.
 *
 * Optimistic updates live in the page component — this composable only
 * handles wire I/O.
 */
export function useSceneGraph() {
  const loading = ref(false);
  const error = ref<SceneGraphError | null>(null);

  async function authHeaders(
    opts: RequestOptions = {},
  ): Promise<Record<string, string> | null> {
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) {
      error.value = {
        status: 401,
        message: sceneGraphErrorMessageForStatus(401),
        detail: "No access token available.",
      };
      return null;
    }
    const headers: Record<string, string> = {
      Authorization: `Bearer ${accessToken}`,
      Accept: opts.acceptJsonLd ? "application/ld+json" : "application/json",
      "Content-Type": "application/json",
    };
    if (opts.aiAgent) headers["X-AI-Agent"] = opts.aiAgent;
    return headers;
  }

  async function readErrorBody(response: Response): Promise<string> {
    try {
      const json = (await response.json()) as { detail?: string; message?: string };
      return json.detail ?? json.message ?? "";
    } catch {
      try {
        return await response.text();
      } catch {
        return "";
      }
    }
  }

  function setNetworkError(e: unknown): void {
    const detail = e instanceof Error ? e.message : "Network error";
    error.value = {
      status: 0,
      message:
        "Network error reaching the scene-graph endpoint — check that the backend is reachable.",
      detail,
    };
  }

  /**
   * SCENEGRAPH-LIST-1 — fetch a page of scene graphs.
   *
   * Hits `GET /v2/scene-graphs?page=<n>&size=<m>`. Returns the parsed page
   * envelope on 2xx; sets {@link error} and resolves to `null` on 4xx/5xx
   * (mirrors {@link fetchScene}'s shape). The backend clamps `size` into
   * `[1, 200]` and defaults to 50.
   */
  async function list(opts: ListOptions = {}): Promise<SceneListPage | null> {
    loading.value = true;
    error.value = null;
    try {
      const headers = await authHeaders({ aiAgent: opts.aiAgent ?? null });
      if (!headers) return null;
      const params = new URLSearchParams();
      if (typeof opts.page === "number") params.set("page", String(opts.page));
      if (typeof opts.size === "number") params.set("size", String(opts.size));
      const qs = params.toString();
      const url = `${v2BaseUrl()}/v2/scene-graphs${qs ? `?${qs}` : ""}`;
      const response = await fetch(url, { method: "GET", headers });
      if (response.ok) {
        return (await response.json()) as SceneListPage;
      }
      const detail = await readErrorBody(response);
      error.value = {
        status: response.status,
        message: sceneGraphErrorMessageForStatus(response.status, detail),
        detail: detail.slice(0, 500),
      };
      return null;
    } catch (e) {
      setNetworkError(e);
      return null;
    } finally {
      loading.value = false;
    }
  }

  async function fetchScene(
    sceneAppId: string,
    opts: RequestOptions = {},
  ): Promise<SceneGraphIO | null> {
    loading.value = true;
    error.value = null;
    try {
      const headers = await authHeaders(opts);
      if (!headers) return null;
      const url = `${v2BaseUrl()}/v2/scene-graphs/${encodeURIComponent(sceneAppId)}`;
      const response = await fetch(url, { method: "GET", headers });
      if (response.ok) {
        return (await response.json()) as SceneGraphIO;
      }
      const detail = await readErrorBody(response);
      error.value = {
        status: response.status,
        message: sceneGraphErrorMessageForStatus(response.status, detail),
        detail: detail.slice(0, 500),
      };
      return null;
    } catch (e) {
      setNetworkError(e);
      return null;
    } finally {
      loading.value = false;
    }
  }

  async function addFrame(
    sceneAppId: string,
    body: CreateFrameRequestIO,
    opts: RequestOptions = {},
  ): Promise<FrameIO | null> {
    error.value = null;
    try {
      const headers = await authHeaders(opts);
      if (!headers) return null;
      const url = `${v2BaseUrl()}/v2/scene-graphs/${encodeURIComponent(sceneAppId)}/frames`;
      const response = await fetch(url, {
        method: "POST",
        headers,
        body: JSON.stringify(body),
      });
      if (response.ok) return (await response.json()) as FrameIO;
      const detail = await readErrorBody(response);
      error.value = {
        status: response.status,
        message: sceneGraphErrorMessageForStatus(response.status, detail),
        detail: detail.slice(0, 500),
      };
      return null;
    } catch (e) {
      setNetworkError(e);
      return null;
    }
  }

  async function patchFrame(
    sceneAppId: string,
    frameAppId: string,
    body: PatchFrameRequestIO,
    opts: RequestOptions = {},
  ): Promise<FrameIO | null> {
    error.value = null;
    try {
      const headers = await authHeaders(opts);
      if (!headers) return null;
      const url = `${v2BaseUrl()}/v2/scene-graphs/${encodeURIComponent(
        sceneAppId,
      )}/frames/${encodeURIComponent(frameAppId)}`;
      const response = await fetch(url, {
        method: "PATCH",
        headers,
        body: JSON.stringify(body),
      });
      if (response.ok) return (await response.json()) as FrameIO;
      const detail = await readErrorBody(response);
      error.value = {
        status: response.status,
        message: sceneGraphErrorMessageForStatus(response.status, detail),
        detail: detail.slice(0, 500),
      };
      return null;
    } catch (e) {
      setNetworkError(e);
      return null;
    }
  }

  async function deleteFrame(
    sceneAppId: string,
    frameAppId: string,
    opts: RequestOptions = {},
  ): Promise<boolean> {
    error.value = null;
    try {
      const headers = await authHeaders(opts);
      if (!headers) return false;
      const url = `${v2BaseUrl()}/v2/scene-graphs/${encodeURIComponent(
        sceneAppId,
      )}/frames/${encodeURIComponent(frameAppId)}`;
      const response = await fetch(url, { method: "DELETE", headers });
      if (response.ok || response.status === 204) return true;
      const detail = await readErrorBody(response);
      error.value = {
        status: response.status,
        message: sceneGraphErrorMessageForStatus(response.status, detail),
        detail: detail.slice(0, 500),
      };
      return false;
    } catch (e) {
      setNetworkError(e);
      return false;
    }
  }

  async function addJoint(
    sceneAppId: string,
    body: CreateJointRequestIO,
    opts: RequestOptions = {},
  ): Promise<JointIO | null> {
    error.value = null;
    try {
      const headers = await authHeaders(opts);
      if (!headers) return null;
      const url = `${v2BaseUrl()}/v2/scene-graphs/${encodeURIComponent(sceneAppId)}/joints`;
      const response = await fetch(url, {
        method: "POST",
        headers,
        body: JSON.stringify(body),
      });
      if (response.ok) return (await response.json()) as JointIO;
      const detail = await readErrorBody(response);
      error.value = {
        status: response.status,
        message: sceneGraphErrorMessageForStatus(response.status, detail),
        detail: detail.slice(0, 500),
      };
      return null;
    } catch (e) {
      setNetworkError(e);
      return null;
    }
  }

  async function deleteJoint(
    sceneAppId: string,
    jointAppId: string,
    opts: RequestOptions = {},
  ): Promise<boolean> {
    error.value = null;
    try {
      const headers = await authHeaders(opts);
      if (!headers) return false;
      const url = `${v2BaseUrl()}/v2/scene-graphs/${encodeURIComponent(
        sceneAppId,
      )}/joints/${encodeURIComponent(jointAppId)}`;
      const response = await fetch(url, { method: "DELETE", headers });
      if (response.ok || response.status === 204) return true;
      const detail = await readErrorBody(response);
      error.value = {
        status: response.status,
        message: sceneGraphErrorMessageForStatus(response.status, detail),
        detail: detail.slice(0, 500),
      };
      return false;
    } catch (e) {
      setNetworkError(e);
      return false;
    }
  }

  /**
   * Download URDF XML. Returns the raw XML string on 2xx; sets `error` on
   * failure. Caller wraps in a Blob + anchor click to trigger the download.
   */
  async function exportUrdf(
    sceneAppId: string,
    opts: RequestOptions = {},
  ): Promise<string | null> {
    error.value = null;
    try {
      const headers = await authHeaders(opts);
      if (!headers) return null;
      // URDF is XML — override the Accept header set by authHeaders.
      headers.Accept = "application/xml";
      const url = `${v2BaseUrl()}/v2/scene-graphs/${encodeURIComponent(
        sceneAppId,
      )}/export.urdf`;
      const response = await fetch(url, { method: "GET", headers });
      if (response.ok) return await response.text();
      const detail = await readErrorBody(response);
      error.value = {
        status: response.status,
        message: sceneGraphErrorMessageForStatus(response.status, detail),
        detail: detail.slice(0, 500),
      };
      return null;
    } catch (e) {
      setNetworkError(e);
      return null;
    }
  }

  return {
    loading,
    error,
    list,
    fetchScene,
    addFrame,
    patchFrame,
    deleteFrame,
    addJoint,
    deleteJoint,
    exportUrdf,
  };
}
