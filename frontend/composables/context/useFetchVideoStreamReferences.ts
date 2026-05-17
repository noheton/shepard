/**
 * UI3a (aidocs/85 §2.2) / UI6 (aidocs/16) — fetch VideoStreamReferences for
 * a DataObject.
 *
 * `VideoStreamReference` is a VID1a entity served at
 * `/v2/data-objects/{dataObjectAppId}/video-stream-references`.
 * It is **not yet regenerated into `@dlr-shepard/backend-client`**, so this
 * composable uses a raw `fetch` call with a manually-typed response shape that
 * mirrors `VideoStreamReferenceIO` exactly.
 *
 * When the backend-client is regenerated (post-VID1a), swap the raw fetch for
 * `useV2ShepardApi(VideoStreamReferenceApi).value.list(...)` and delete this
 * file.
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

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
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

    const url = `${v2BaseUrl()}/v2/data-objects/${encodeURIComponent(dataObjectAppId)}/video-stream-references`;

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
      references.value = (await response.json()) as VideoStreamReferenceIO[];
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
   * Builds the direct-download URL for a VideoStreamReference.
   * VID1a provides a raw file download; VID1b+ will add HLS segmented delivery.
   */
  function downloadUrl(appId: string): string {
    return `${v2BaseUrl()}/v2/data-objects/${encodeURIComponent(dataObjectAppId)}/video-stream-references/${encodeURIComponent(appId)}/download`;
  }

  refresh();

  return { references, isLoading, fetchError, refresh, downloadUrl };
}
