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
}

export interface SceneGraphError {
  status: number;
  message: string;
  detail: string;
}

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

export interface SceneListPage {
  items: SceneListItem[];
  total: number;
  page: number;
  size: number;
}

export interface ListOptions {
  page?: number;
  size?: number;
}

export function sceneGraphErrorMessageForStatus(
  status: number,
  fallback?: string,
): string {
  switch (status) {
    case 401:
      return "Sign in expired — refresh the page.";
    case 403:
      return "You don't have access to this scene.";
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

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export function useSceneGraph() {
  const loading = ref(false);
  const error = ref<SceneGraphError | null>(null);

  async function authHeaders(): Promise<Record<string, string> | null> {
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
    return {
      Authorization: `Bearer ${accessToken}`,
      Accept: "application/json",
      "Content-Type": "application/json",
    };
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

  async function list(opts: ListOptions = {}): Promise<SceneListPage | null> {
    loading.value = true;
    error.value = null;
    try {
      const headers = await authHeaders();
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

  async function fetchScene(sceneAppId: string): Promise<SceneGraphIO | null> {
    loading.value = true;
    error.value = null;
    try {
      const headers = await authHeaders();
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

  return {
    loading,
    error,
    list,
    fetchScene,
  };
}
