/**
 * Frontend wrapper for the TA1a + AI1b endpoints (APISIMP-TSREF-NS-COLLAPSE):
 *
 *   GET    /v2/references/{appId}/annotations
 *   POST   /v2/references/{appId}/annotations
 *   DELETE /v2/references/{appId}/annotations/{annotationAppId}
 *   POST   /v2/references/{appId}/detect-anomalies
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

/** One contiguous run of anomalous data points — mirrors AnomalyIntervalIO. */
export interface AnomalyIntervalDto {
  startNs: number;
  endNs: number;
  peakValue: number;
  maxZScore: number;
}

/** Full result from POST /detect-anomalies — mirrors AnomalyDetectResultIO. */
export interface AnomalyDetectionResultDto {
  anomalies: AnomalyIntervalDto[];
  windowSize: number;
  threshold: number;
  totalPoints: number;
  annotationsCreated: number;
}

/** Parameters forwarded in the JSON body to the detect-anomalies endpoint. */
export interface DetectAnomaliesParams {
  window?: number;
  k?: number;
  createAnnotations?: boolean;
  measurement?: string;
  field?: string;
  detectorId?: string;
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
      const url = `${v2BaseUrl()}/v2/references/${refAppId.value}/annotations`;
      const response = await fetch(url, { headers });
      if (response.ok) {
        const page = await response.json();
        annotations.value = (page.items ?? page) as TimeseriesAnnotationDto[];
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
      const url = `${v2BaseUrl()}/v2/references/${refAppId.value}/annotations/${annotationAppId}`;
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

  /**
   * POST /v2/references/{refAppId}/detect-anomalies
   *
   * Sends the detection parameters as a JSON body (the endpoint accepts an
   * optional body — empty {} uses server defaults). Returns the full
   * AnomalyDetectResultIO payload so the dialog can display per-interval detail.
   *
   * Set `createAnnotations: true` to have the server persist
   * TimeseriesAnnotation nodes; the composable calls fetchAll() afterwards so
   * the "Anomalies & intervals" section refreshes automatically.
   */
  async function detectAnomalies(
    params: DetectAnomaliesParams = {},
  ): Promise<AnomalyDetectionResultDto | null> {
    if (!refAppId.value) return null;
    detecting.value = true;
    try {
      const headers = await authHeaders();
      const url =
        `${v2BaseUrl()}/v2/references/${refAppId.value}/detect-anomalies`;
      const response = await fetch(url, {
        method: "POST",
        headers,
        body: JSON.stringify(params),
      });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const result = (await response.json()) as AnomalyDetectionResultDto;
      emitSuccess(
        `Anomaly detection finished — ${result.anomalies.length} interval(s) found.`,
      );
      if (params.createAnnotations) {
        await fetchAll();
      }
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
