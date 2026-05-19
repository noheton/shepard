/**
 * VID1b-annotation — fetch VideoAnnotations for a VideoStreamReference.
 *
 * Annotations are served at
 * `/v2/data-objects/{dataObjectAppId}/video-stream-references/{refAppId}/annotations`.
 * Uses a raw fetch call with a manually-typed response shape that mirrors
 * VideoAnnotationIO exactly, consistent with the useFetchVideoStreamReferences pattern.
 */

export interface VideoAnnotationIO {
  appId: string;
  startSeconds: number;
  endSeconds: number | null;
  label: string;
  description: string | null;
  aiGenerated: boolean;
  confidence: number | null;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export function useFetchVideoAnnotations(
  dataObjectAppId: string,
  refAppId: string,
) {
  const annotations = ref<VideoAnnotationIO[]>([]);
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
      `${v2BaseUrl()}/v2/data-objects/${encodeURIComponent(dataObjectAppId)}` +
      `/video-stream-references/${encodeURIComponent(refAppId)}/annotations`;

    try {
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (!response.ok) {
        const bodyText = await response.text().catch(() => "");
        fetchError.value = `Failed to fetch video annotations (HTTP ${response.status}): ${bodyText.slice(0, 200)}`;
        handleError(fetchError.value, "listVideoAnnotations");
        return;
      }
      annotations.value = (await response.json()) as VideoAnnotationIO[];
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Network error";
      fetchError.value = message;
      handleError(message, "listVideoAnnotations");
    } finally {
      isLoading.value = false;
    }
  }

  refresh();

  return { annotations, isLoading, fetchError, refresh };
}
