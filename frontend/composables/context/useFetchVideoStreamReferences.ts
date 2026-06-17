/**
 * UI3a (aidocs/85 §2.2) / UI6 (aidocs/16) — fetch VideoStreamReferences for
 * a DataObject via the unified `/v2/references?kind=video` surface.
 *
 * PLUGIN-REF-HANDLER-FE-REPOINT: migrated from the plugin-specific
 * `/v2/data-objects/{appId}/video-stream-references` path to the unified
 * `GET /v2/references?kind=video&dataObjectAppId={appId}` endpoint now that
 * the `video` ReferenceKindHandler is installed (merged in bfab5f04b).
 *
 * The per-kind `payload` map carries:
 *   storageLocator, mimeType, fileSizeBytes, durationSeconds, width, height,
 *   frameRate, videoCodec, audioCodec, wallClockTimestamp
 * which are mapped back to the typed `VideoStreamReferenceIO` interface so
 * consumers (`VideoStreamReferencesPane.vue`, `dataTableElementMappingUtil.ts`)
 * require no changes.
 *
 * The `/download` URL builder stays on the plugin-specific path — it is a
 * kind-specific binary op outside the unified surface (PLUGIN-REF-HANDLER-*
 * in aidocs/16).
 */

export interface VideoStreamReferenceIO {
  appId: string;
  id?: number;
  name?: string | null;
  createdAt?: string | null;
  createdBy?: string | null;
  mimeType?: string | null;
  fileSizeBytes?: number | null;
  durationSeconds?: number | null;
  width?: number | null;
  height?: number | null;
  frameRate?: number | null;
  videoCodec?: string | null;
  audioCodec?: string | null;
  wallClockTimestamp?: number | null;
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

function toVideoStreamReferenceIO(r: ReferenceV2IO): VideoStreamReferenceIO {
  const p = r.payload;
  return {
    appId: r.appId,
    id: r.id,
    name: r.name,
    createdAt: r.createdAt,
    createdBy: r.createdBy,
    mimeType: (p.mimeType as string | null | undefined) ?? null,
    fileSizeBytes: (p.fileSizeBytes as number | null | undefined) ?? null,
    durationSeconds: (p.durationSeconds as number | null | undefined) ?? null,
    width: (p.width as number | null | undefined) ?? null,
    height: (p.height as number | null | undefined) ?? null,
    frameRate: (p.frameRate as number | null | undefined) ?? null,
    videoCodec: (p.videoCodec as string | null | undefined) ?? null,
    audioCodec: (p.audioCodec as string | null | undefined) ?? null,
    wallClockTimestamp: (p.wallClockTimestamp as number | null | undefined) ?? null,
  };
}

export function useFetchVideoStreamReferences(dataObjectAppId: string) {
  const references = ref<VideoStreamReferenceIO[]>([]);
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
      `?kind=video&dataObjectAppId=${encodeURIComponent(dataObjectAppId)}`;

    try {
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (!response.ok) {
        const bodyText = await response.text().catch(() => "");
        fetchError.value = `Failed to fetch video references (HTTP ${response.status}): ${bodyText.slice(0, 200)}`;
        handleError(fetchError.value, "listVideoStreamReferences");
        return;
      }
      const raw = (await response.json()) as ReferenceV2IO[];
      references.value = raw.map(toVideoStreamReferenceIO);
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Network error";
      fetchError.value = message;
      handleError(message, "listVideoStreamReferences");
    } finally {
      isLoading.value = false;
    }
  }

  /**
   * APISIMP-VIDEO-STREAMREF-PATH: download URL now routes through the unified
   * GET /v2/references/{appId}/content endpoint (range-aware, same 206 semantics).
   * The old /v2/data-objects/{doId}/video-stream-references/{appId}/download path
   * returns 410 Gone.
   */
  function downloadUrl(appId: string): string {
    return `${v2BaseUrl()}/v2/references/${encodeURIComponent(appId)}/content`;
  }

  refresh();

  return { references, isLoading, fetchError, refresh, downloadUrl };
}
