/**
 * TH1b — fetches a thumbnail for a single file in a FileContainer.
 *
 * Endpoint: GET /v2/file-containers/{containerAppId}/payload/{oid}/thumbnail?size=200
 *
 * Returns a blob URL (revoke when component unmounts) or null when the
 * file type is unsupported (HTTP 404) or generation is unavailable (HTTP 503).
 * Callers should fall back to a file-type icon on null.
 */

export type ThumbnailSize = 64 | 200 | 400;

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export function useFetchFileThumbnail(
  containerAppId: string,
  oid: string,
  size: ThumbnailSize = 200,
) {
  const blobUrl = ref<string | null>(null);
  const isLoading = ref(false);
  const isUnsupported = ref(false);

  async function load() {
    isLoading.value = true;
    isUnsupported.value = false;

    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) {
      isLoading.value = false;
      return;
    }

    const url =
      `${v2BaseUrl()}/v2/file-containers/` +
      `${encodeURIComponent(containerAppId)}/payload/` +
      `${encodeURIComponent(oid)}/thumbnail?size=${size}`;

    try {
      const resp = await fetch(url, {
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      if (resp.status === 404 || resp.status === 503) {
        isUnsupported.value = true;
        return;
      }
      if (!resp.ok) return;

      const blob = await resp.blob();
      if (blobUrl.value) URL.revokeObjectURL(blobUrl.value);
      blobUrl.value = URL.createObjectURL(blob);
    } catch {
      // network failure — treat as unsupported
      isUnsupported.value = true;
    } finally {
      isLoading.value = false;
    }
  }

  function revoke() {
    if (blobUrl.value) {
      URL.revokeObjectURL(blobUrl.value);
      blobUrl.value = null;
    }
  }

  return { blobUrl, isLoading, isUnsupported, load, revoke };
}
