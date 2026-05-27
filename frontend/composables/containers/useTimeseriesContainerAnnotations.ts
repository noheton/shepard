/**
 * TS-ANNOT-B — container-scoped temporal annotations.
 *
 * Wraps the raw-fetch CRUD surface at:
 *   GET    /v2/timeseries-containers/{containerId}/temporal-annotations
 *   POST   /v2/timeseries-containers/{containerId}/temporal-annotations
 *   PATCH  /v2/timeseries-containers/{containerId}/temporal-annotations/{appId}
 *   DELETE /v2/timeseries-containers/{containerId}/temporal-annotations/{appId}
 *
 * The OpenAPI generator hasn't been re-run since this endpoint shipped, so we
 * use raw fetch — matching the pattern in useTimeseriesReferenceAnnotations.ts.
 */

export interface ContainerAnnotationDto {
  appId: string;
  startNs: number;
  endNs: number | null;
  label: string;
  description?: string | null;
  aiGenerated?: boolean;
  confidence?: number | null;
}

export interface CreateAnnotationBody {
  startNs: number;
  endNs?: number | null;
  label: string;
  description?: string | null;
}

export interface PatchAnnotationBody {
  startNs?: number;
  endNs?: number | null;
  label?: string;
  description?: string | null;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = (config as { backendV2ApiUrl?: string }).backendV2ApiUrl;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function authHeaders(): Promise<Record<string, string>> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  if (!accessToken) throw new Error("Not authenticated");
  return {
    Authorization: `Bearer ${accessToken}`,
    Accept: "application/json",
    "Content-Type": "application/json",
  };
}

export function useTimeseriesContainerAnnotations(containerId: Ref<number | undefined>) {
  const annotations = ref<ContainerAnnotationDto[]>([]);
  const loading = ref(false);
  const saving = ref(false);

  async function fetchAll() {
    if (!containerId.value) return;
    loading.value = true;
    try {
      const headers = await authHeaders();
      const url = `${v2BaseUrl()}/v2/timeseries-containers/${containerId.value}/temporal-annotations`;
      const response = await fetch(url, { headers });
      if (response.ok) {
        annotations.value = (await response.json()) as ContainerAnnotationDto[];
      } else {
        annotations.value = [];
      }
    } catch (e) {
      handleError(e as Error, "fetching container annotations");
    } finally {
      loading.value = false;
    }
  }

  async function createAnnotation(body: CreateAnnotationBody): Promise<ContainerAnnotationDto | null> {
    if (!containerId.value) return null;
    saving.value = true;
    try {
      const headers = await authHeaders();
      const url = `${v2BaseUrl()}/v2/timeseries-containers/${containerId.value}/temporal-annotations`;
      const response = await fetch(url, {
        method: "POST",
        headers,
        body: JSON.stringify(body),
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const created = (await response.json()) as ContainerAnnotationDto;
      annotations.value = [...annotations.value, created];
      emitSuccess("Annotation created");
      return created;
    } catch (e) {
      handleError(e as Error, "creating annotation");
      return null;
    } finally {
      saving.value = false;
    }
  }

  async function updateAnnotation(appId: string, body: PatchAnnotationBody): Promise<boolean> {
    if (!containerId.value) return false;
    saving.value = true;
    try {
      const headers = await authHeaders();
      const url = `${v2BaseUrl()}/v2/timeseries-containers/${containerId.value}/temporal-annotations/${appId}`;
      const response = await fetch(url, {
        method: "PATCH",
        headers,
        body: JSON.stringify(body),
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const updated = (await response.json()) as ContainerAnnotationDto;
      annotations.value = annotations.value.map(a => a.appId === appId ? updated : a);
      emitSuccess("Annotation updated");
      return true;
    } catch (e) {
      handleError(e as Error, "updating annotation");
      return false;
    } finally {
      saving.value = false;
    }
  }

  async function deleteAnnotation(appId: string): Promise<void> {
    if (!containerId.value) return;
    try {
      const headers = await authHeaders();
      const url = `${v2BaseUrl()}/v2/timeseries-containers/${containerId.value}/temporal-annotations/${appId}`;
      const response = await fetch(url, { method: "DELETE", headers });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      annotations.value = annotations.value.filter(a => a.appId !== appId);
      emitSuccess("Annotation deleted");
    } catch (e) {
      handleError(e as Error, "deleting annotation");
    }
  }

  watch(containerId, () => fetchAll(), { immediate: true });

  return {
    annotations,
    loading,
    saving,
    fetchAll,
    createAnnotation,
    updateAnnotation,
    deleteAnnotation,
  };
}
