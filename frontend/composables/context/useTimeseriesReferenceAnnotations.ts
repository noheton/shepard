/**
 * Frontend wrapper for the TA1a + AI1b endpoints:
 *
 *   GET    /v2/timeseries-references/{refAppId}/annotations
 *   POST   /v2/timeseries-references/{refAppId}/annotations
 *   DELETE /v2/timeseries-references/{refAppId}/annotations/{annotationAppId}
 *   POST   /v2/timeseries-references/{refAppId}/detect-anomalies
 *
 * The OpenAPI generator hasn't been re-run since TA1a / AI1b shipped, so we
 * talk these endpoints with raw fetch — matching the pattern used for
 * container-level annotations and safe-delete.
 */

export interface TimeseriesAnnotationDto {
  appId: string;
  refAppId: string;
  startNs: number;
  endNs: number;
  label: string;
  description?: string | null;
  aiGenerated?: boolean;
  confidence?: number | null;
  createdAt?: string;
  createdBy?: string;
}

export interface AnomalyDetectionResultDto {
  annotationsCreated: number;
  intervalsDetected: number;
  // Plus the raw stats payload — fields vary; we don't render them yet.
  [k: string]: unknown;
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

export function useTimeseriesReferenceAnnotations(refAppId: Ref<string | undefined>) {
  const annotations = ref<TimeseriesAnnotationDto[]>([]);
  const loading = ref(false);
  const detecting = ref(false);

  async function fetchAll() {
    if (!refAppId.value) return;
    loading.value = true;
    try {
      const headers = await authHeaders();
      const url = `${v2BaseUrl()}/v2/timeseries-references/${refAppId.value}/annotations`;
      const response = await fetch(url, { headers });
      if (response.ok) {
        annotations.value = (await response.json()) as TimeseriesAnnotationDto[];
      } else {
        annotations.value = [];
      }
    } catch (e) {
      handleError(e as Error, "fetching timeseries annotations");
    } finally {
      loading.value = false;
    }
  }

  async function deleteAnnotation(annotationAppId: string) {
    if (!refAppId.value) return;
    try {
      const headers = await authHeaders();
      const url = `${v2BaseUrl()}/v2/timeseries-references/${refAppId.value}/annotations/${annotationAppId}`;
      const response = await fetch(url, { method: "DELETE", headers });
      if (response.ok) {
        annotations.value = annotations.value.filter(a => a.appId !== annotationAppId);
        emitSuccess("Annotation deleted");
      } else {
        throw new Error(`HTTP ${response.status}`);
      }
    } catch (e) {
      handleError(e as Error, "deleting annotation");
    }
  }

  async function detectAnomalies(): Promise<AnomalyDetectionResultDto | null> {
    if (!refAppId.value) return null;
    detecting.value = true;
    try {
      const headers = await authHeaders();
      const url =
        `${v2BaseUrl()}/v2/timeseries-references/${refAppId.value}` +
        `/detect-anomalies?createAnnotations=true`;
      const response = await fetch(url, { method: "POST", headers });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const result = (await response.json()) as AnomalyDetectionResultDto;
      emitSuccess(
        `Anomaly detection finished — ${result.intervalsDetected ?? 0} interval(s) found.`,
      );
      await fetchAll();
      return result;
    } catch (e) {
      handleError(e as Error, "running anomaly detection");
      return null;
    } finally {
      detecting.value = false;
    }
  }

  watch(refAppId, () => fetchAll(), { immediate: true });

  return {
    annotations,
    loading,
    detecting,
    fetchAll,
    deleteAnnotation,
    detectAnomalies,
  };
}
