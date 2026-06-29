/**
 * REF-UNIFIED-TABLE-FR1B — fetches the FR1b singleton FileReferences
 * attached to a DataObject so the unified data-references table can
 * render them alongside FR1a bundles + timeseries + structured-data.
 *
 * V2CONV-A2: backed by the unified list endpoint
 * `GET /v2/references?kind=file&dataObjectAppId={appId}` (the legacy
 * `GET /v2/files/by-data-object/{dataObjectAppId}` returns 410 since
 * APISIMP-FILE-PATH-RETIRE-2). The unified response is
 * `ReferenceV2IO[]`: the embedded `ShepardFile` lives under `payload.file`,
 * the FR1b discriminator is `referenceShape === "singleton"`, and the
 * file-kind is `fileKind`. This composable normalises that envelope back to
 * the flat `SingletonFileReferenceIO` shape its callers already consume. The
 * upstream v1 list endpoint
 * `GET /shepard/api/collections/{collectionId}/dataObjects/{dataObjectId}/fileReferences`
 * stays byte-frozen and returns only FR1a bundle shapes; this composable's
 * additive read complements it.
 */

import type { ShepardFile } from "@dlr-shepard/backend-client";

export interface SingletonFileReferenceIO {
  appId: string;
  name: string;
  dataObjectId?: number;
  createdAt: string;
  createdBy: string;
  type?: string;
  file: ShepardFile | null;
  /** V2CONV-A2 file-kind discriminator (krl, urdf, pdf, …) or null. */
  fileKind?: string | null;
}

/** V2CONV-A2 unified envelope shape (subset consumed here). */
interface ReferenceV2IO {
  appId: string;
  name: string;
  dataObjectId?: number;
  createdAt: string;
  createdBy: string;
  type?: string;
  kind?: string;
  referenceShape?: string | null;
  fileKind?: string | null;
  payload?: { file?: ShepardFile | null } | null;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export function useFetchSingletonFileReferences(
  dataObjectAppId: Ref<string | undefined> | string | undefined,
) {
  const references = ref<SingletonFileReferenceIO[]>([]);
  const isLoading = ref(false);
  const error = ref<string | null>(null);

  async function refresh() {
    const appId = isRef(dataObjectAppId) ? dataObjectAppId.value : dataObjectAppId;
    if (!appId) {
      references.value = [];
      return;
    }
    isLoading.value = true;
    error.value = null;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const url = `${v2BaseUrl()}/v2/references?kind=file&dataObjectAppId=${encodeURIComponent(appId)}`;
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (!response.ok) {
        // 404 (DO missing) and 403 (no permission) treated as empty.
        if (response.status === 404 || response.status === 403) {
          references.value = [];
          return;
        }
        throw new Error(`HTTP ${response.status}`);
      }
      // BUG-DO-DETAIL-A-TOAST-2026-06-29: unwrap the paged envelope shape
      // { items: [...] } the unified /v2/references list now returns.
      const body = (await response.json()) as
        | ReferenceV2IO[]
        | { items?: ReferenceV2IO[] };
      const unified = Array.isArray(body)
        ? body
        : ((body as { items?: ReferenceV2IO[] }).items ?? []);
      // Normalise the unified envelope back to the flat shape callers expect.
      references.value = unified
        .filter((r) => r.referenceShape === "singleton" || r.payload?.file !== undefined)
        .map((r) => ({
          appId: r.appId,
          name: r.name,
          dataObjectId: r.dataObjectId,
          createdAt: r.createdAt,
          createdBy: r.createdBy,
          type: r.type,
          file: r.payload?.file ?? null,
          fileKind: r.fileKind ?? null,
        }));
    } catch (e) {
      error.value = "Failed to load singleton file references";
      handleError(e, "fetching singleton file references");
      references.value = [];
    } finally {
      isLoading.value = false;
    }
  }

  if (isRef(dataObjectAppId)) {
    watch(dataObjectAppId, () => refresh(), { immediate: true });
  } else {
    refresh();
  }

  return { references, isLoading, error, refresh };
}
