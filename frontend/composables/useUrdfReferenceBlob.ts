/**
 * SCENEGRAPH-CANVAS-1 — resolve a URDF FileReference's bytes into a
 * browser-loadable object URL.
 *
 * Honours the "UI never asks for paths/URLs — pulls from references" rule:
 * the caller passes a singleton FileReference `appId` (the scene's
 * `sourceFileAppId`), and this composable fetches the bytes from the
 * authenticated v2 content endpoint `GET /v2/files/{appId}/content`,
 * wrapping them in an `ObjectURL`. The frontend never constructs a Garage
 * signed URL or a storage path itself.
 *
 * Why an object URL (vs. handing `urdf-loader` the content endpoint
 * directly): `urdf-loader` issues a bare `fetch`/XHR with no Authorization
 * header, so a protected `/v2/files/{appId}/content` would 401. Fetching
 * here with the bearer token and materialising a `blob:` URL lets the
 * loader read the bytes with zero auth knowledge. Mesh assets bundled in
 * the same FileReference are NOT object-URL-rewritten (a future
 * SCENEGRAPH-CANVAS-MESH row tracks `package://` mesh resolution for
 * multi-file robot bundles — single-file primitive URDFs render today).
 *
 * Mirrors the fetch + runtime-config pattern in useSceneGraph.ts /
 * useScenegraphFromUrdf.ts so the three URDF-adjacent composables share one
 * shape.
 */

/**
 * Build the v2 file-content URL for a FileReference appId.
 *
 * Pure + exported so the unit test asserts the URL shape without a network
 * round-trip. Strips a trailing `/shepard/api` (the v1 carrier base) so the
 * result always lands on the `/v2/` shelf.
 */
export function v2FileContentUrl(backendApiUrl: string, appId: string): string {
  const base = backendApiUrl
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
  return `${base}/v2/files/${encodeURIComponent(appId)}/content`;
}

export interface UrdfBlobError {
  status: number;
  message: string;
}

/**
 * Map an HTTP status from the content endpoint to a user-facing message.
 * Exported so the panel + tests render identical copy.
 */
export function urdfBlobErrorForStatus(status: number): string {
  switch (status) {
    case 401:
      return "Sign in expired — refresh the page.";
    case 403:
      return "You don't have access to this URDF file.";
    case 404:
      return "The source URDF FileReference no longer exists.";
    case 0:
      return "Network error fetching the URDF — check the backend is reachable.";
    default:
      return `Could not load the URDF file (HTTP ${status}).`;
  }
}

function v2BackendBase(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return config.backendApiUrl as string;
}

/**
 * Fetch a URDF FileReference's bytes and expose a reactive `objectUrl`
 * suitable for {@link ~/components/shapes/UrdfCanvas.vue}'s `urdfUrl` prop.
 *
 * The caller is responsible for calling {@link revoke} on unmount (the
 * panel does this in `onUnmounted`) to avoid leaking the `blob:` URL.
 */
export function useUrdfReferenceBlob() {
  const objectUrl = ref<string | null>(null);
  const loading = ref(false);
  const error = ref<UrdfBlobError | null>(null);

  function revoke(): void {
    if (objectUrl.value) {
      URL.revokeObjectURL(objectUrl.value);
      objectUrl.value = null;
    }
  }

  /**
   * Resolve `fileReferenceAppId` → `blob:` object URL. Re-resolving revokes
   * the previous URL first. Returns the URL on success or null on failure
   * (with {@link error} set).
   */
  async function resolve(fileReferenceAppId: string): Promise<string | null> {
    revoke();
    loading.value = true;
    error.value = null;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      if (!accessToken) {
        error.value = { status: 401, message: urdfBlobErrorForStatus(401) };
        return null;
      }
      const url = v2FileContentUrl(v2BackendBase(), fileReferenceAppId);
      const response = await fetch(url, {
        method: "GET",
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      if (!response.ok) {
        error.value = {
          status: response.status,
          message: urdfBlobErrorForStatus(response.status),
        };
        return null;
      }
      const blob = await response.blob();
      const created = URL.createObjectURL(blob);
      objectUrl.value = created;
      return created;
    } catch {
      error.value = { status: 0, message: urdfBlobErrorForStatus(0) };
      return null;
    } finally {
      loading.value = false;
    }
  }

  return { objectUrl, loading, error, resolve, revoke };
}
