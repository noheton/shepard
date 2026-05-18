/**
 * DI1 — call the /v2/{kind}-containers/{id} safe-delete endpoint.
 *
 * Returns:
 *   - { ok: true } if the container was deleted (HTTP 204)
 *   - { ok: false, conflict } if the server refused because of active references (HTTP 409)
 *   - throws on any other failure (auth, 5xx, network)
 *
 * Callers typically:
 *   1. Call without `force` first.
 *   2. If they get `{ ok: false }`, surface the conflict to the user.
 *   3. Retry with `force: true` if the user explicitly confirms.
 */

export interface SafeDeleteConflictInfo {
  referenceCount: number;
  sampleDataObjectAppIds: string[];
}

export type SafeDeleteKind = "timeseries" | "file" | "structured-data";

const pathByKind: Record<SafeDeleteKind, string> = {
  timeseries: "timeseries-containers",
  file: "file-containers",
  "structured-data": "structured-data-containers",
};

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export async function safeDeleteContainer(
  kind: SafeDeleteKind,
  containerId: number,
  options: { force?: boolean } = {},
): Promise<
  | { ok: true }
  | { ok: false; conflict: SafeDeleteConflictInfo }
> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  if (!accessToken) throw new Error("Not authenticated");

  const force = options.force ?? false;
  const url =
    `${v2BaseUrl()}/v2/${pathByKind[kind]}/${containerId}` +
    (force ? "?force=true" : "");

  const response = await fetch(url, {
    method: "DELETE",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      Accept: "application/json",
    },
  });

  if (response.status === 204) return { ok: true };
  if (response.status === 409) {
    const conflict = (await response.json()) as SafeDeleteConflictInfo;
    return { ok: false, conflict };
  }
  // Anything else: bubble up the standard handleError flow.
  const text = await response.text().catch(() => "");
  throw new Error(`HTTP ${response.status} ${text}`);
}
