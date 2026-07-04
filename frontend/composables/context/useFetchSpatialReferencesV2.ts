/**
 * SPATIAL-UNIFY-003 — fetch SpatialDataReferences for a DataObject via the
 * unified `GET /v2/references?kind=spatial&dataObjectAppId={appId}` surface.
 *
 * This is the frontend half of the spatial unification (aidocs/integrations/124):
 * spatial is a reference kind like File / TimeSeries / Video, addressed by the
 * reference `appId`. The per-kind `payload` map carries:
 *   geometryFilter, measurementsFilter, startTime, endTime, metadata, limit,
 *   skip, spatialDataContainerAppId, promotionState
 *
 * The `spatialDataContainerAppId` is the viewer target — the SpatialDataContainer
 * stays an implementation detail behind the reference (the user never picks a
 * container). appId everywhere; no numeric ids.
 */

export interface SpatialReferenceV2IO {
  appId: string;
  id?: number;
  name?: string | null;
  createdAt?: string | null;
  createdBy?: string | null;
  spatialDataContainerAppId?: string | null;
  promotionState?: string | null;
}

interface ReferenceV2IO {
  appId: string;
  id?: number;
  name?: string | null;
  createdAt?: string | null;
  createdBy?: string | null;
  kind: string;
  payload: Record<string, unknown>;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

function toSpatialReferenceV2IO(r: ReferenceV2IO): SpatialReferenceV2IO {
  const p = r.payload ?? {};
  return {
    appId: r.appId,
    id: r.id,
    name: r.name,
    createdAt: r.createdAt,
    createdBy: r.createdBy,
    spatialDataContainerAppId:
      (p.spatialDataContainerAppId as string | null | undefined) ?? null,
    promotionState: (p.promotionState as string | null | undefined) ?? null,
  };
}

export function useFetchSpatialReferencesV2(dataObjectAppId: string) {
  const references = ref<SpatialReferenceV2IO[]>([]);
  const isLoading = ref(false);
  const fetchError = ref<string | null>(null);

  async function refresh() {
    isLoading.value = true;
    fetchError.value = null;

    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) {
      fetchError.value = "Not authenticated";
      isLoading.value = false;
      return;
    }

    const url =
      `${v2BaseUrl()}/v2/references` +
      `?kind=spatial&dataObjectAppId=${encodeURIComponent(dataObjectAppId)}`;

    try {
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (!response.ok) {
        const bodyText = await response.text().catch(() => "");
        fetchError.value = `Failed to fetch spatial references (HTTP ${response.status}): ${bodyText.slice(0, 200)}`;
        handleError(fetchError.value, "listSpatialReferences");
        return;
      }
      // BUG-DO-DETAIL-A-TOAST-2026-06-29: unwrap the paged envelope shape
      // { items: [...] } the unified /v2/references list now returns.
      const body = (await response.json()) as
        | ReferenceV2IO[]
        | { items?: ReferenceV2IO[] };
      const raw = Array.isArray(body)
        ? body
        : ((body as { items?: ReferenceV2IO[] }).items ?? []);
      references.value = raw.map(toSpatialReferenceV2IO);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Network error";
      fetchError.value = message;
      handleError(message, "listSpatialReferences");
    } finally {
      isLoading.value = false;
    }
  }

  refresh();

  return { references, isLoading, fetchError, refresh };
}
