/**
 * SPATIAL-UNIFY-004 — the in-context "Promote to spatial" action.
 *
 * Calls `POST /v2/spatial/promote?fileReferenceAppId={appId}` for an eligible
 * singleton FileReference (pointcloud / trajectory). The backend mints a
 * SpatialDataReference + its backing SpatialDataContainer and enqueues the
 * spatial-importer sidecar. Idempotent: re-promoting returns the existing
 * spatial reference (200 vs 201).
 *
 * The user never types a path — the appId is already in hand on the File row
 * (CLAUDE.md "tools in-context first" + "UI never asks for paths/URLs").
 */

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

/**
 * Heuristic: is a File row's filename an eligible spatial payload? Mirrors the
 * backend SpatialFileClassifier so the "Promote to spatial" affordance only
 * shows for files the backend will accept.
 */
const POINTCLOUD_EXTENSIONS = [".las", ".laz", ".ply", ".e57", ".pcd", ".xyz", ".pts"];
const NAME_SIGNALS = [
  "tps 3d pointclouds",
  "fsd course 3d pointclouds",
  "tps raw data",
  "pointcloud",
  "trajectory",
];

export function isSpatialEligibleName(name: string | null | undefined): boolean {
  if (!name) return false;
  const lower = name.toLowerCase();
  if (POINTCLOUD_EXTENSIONS.some(ext => lower.endsWith(ext))) return true;
  return NAME_SIGNALS.some(sig => lower.includes(sig));
}

export function usePromoteToSpatial() {
  const isPromoting = ref(false);

  /**
   * Promote a FileReference into a spatial reference.
   *
   * @param fileReferenceAppId the source singleton FileReference appId
   * @returns true on success (201 created or 200 already-promoted)
   */
  async function promote(fileReferenceAppId: string): Promise<boolean> {
    isPromoting.value = true;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      if (!accessToken) {
        handleError("Not authenticated", "promoteToSpatial");
        return false;
      }
      const url =
        `${v2BaseUrl()}/v2/spatial/promote` +
        `?fileReferenceAppId=${encodeURIComponent(fileReferenceAppId)}`;
      const response = await fetch(url, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (response.ok || response.status === 201 || response.status === 200) {
        return true;
      }
      const bodyText = await response.text().catch(() => "");
      handleError(`HTTP ${response.status}: ${bodyText.slice(0, 200)}`, "promoteToSpatial");
      return false;
    } catch (error) {
      handleError(error, "promoteToSpatial");
      return false;
    } finally {
      isPromoting.value = false;
    }
  }

  return { promote, isPromoting };
}
