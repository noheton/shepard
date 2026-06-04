/**
 * V2CONV-A3 — create a container via the unified
 * `POST /v2/containers?kind={kind}` surface.
 *
 * Returns the parsed unified ContainerV2 envelope, which carries the common
 * fields callers consume (`id`, `appId`, `name`, `type`, `status`) plus a
 * per-kind read-only `payload` (e.g. `payload.oid`).
 *
 * Implemented as a hand-written `fetch` because the generated
 * `@dlr-shepard/backend-client` does not yet carry a `ContainersV2Api` method
 * (regenerating the client requires the OpenAPI toolchain not available in this
 * worktree — see the V2CONV-A3 report). Mirrors the A2 `/v2/references` fetch
 * helpers.
 */

export type V2ContainerKind = "file" | "timeseries" | "structured-data";

export interface V2Container {
  id: number;
  appId?: string;
  name: string;
  type?: string;
  status?: string | null;
  kind: string;
  payload?: Record<string, unknown>;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export async function createV2Container(
  kind: V2ContainerKind,
  name: string,
): Promise<V2Container | undefined> {
  try {
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    const headers: Record<string, string> = {
      "Content-Type": "application/json",
      Accept: "application/json",
    };
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
    const resp = await fetch(
      `${v2BaseUrl()}/v2/containers?kind=${encodeURIComponent(kind)}`,
      {
        method: "POST",
        headers,
        body: JSON.stringify({ name }),
      },
    );
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    return (await resp.json()) as V2Container;
  } catch (e) {
    handleError(e as Error, `creating ${kind} container`);
    return undefined;
  }
}

/**
 * V2CONV-A3 — list containers of a kind via
 * `GET /v2/containers?kind={kind}[&name=…]`. Returns the unified envelope
 * array (possibly empty). Same hand-written-fetch rationale as
 * {@link createV2Container}.
 */
export async function listV2Containers(
  kind: V2ContainerKind,
  nameFilter?: string,
): Promise<V2Container[]> {
  try {
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    const headers: Record<string, string> = { Accept: "application/json" };
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
    const q =
      `kind=${encodeURIComponent(kind)}` +
      (nameFilter ? `&name=${encodeURIComponent(nameFilter)}` : "");
    const resp = await fetch(`${v2BaseUrl()}/v2/containers?${q}`, {
      method: "GET",
      headers,
    });
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    return (await resp.json()) as V2Container[];
  } catch (e) {
    handleError(e as Error, `listing ${kind} containers`);
    return [];
  }
}
