/**
 * PV1a — fetches the byte-level version history for a single file in a FileContainer.
 *
 * Endpoint: GET /v2/file-containers/{containerAppId}/files/{fileName}/versions
 *
 * Lazy: call load() explicitly (e.g. when the history dialog opens).
 * Not called on composable construction to avoid network traffic for every
 * table row.
 */

export interface PayloadVersionIO {
  appId: string;
  versionNumber: number;
  fileOid: string | null;
  sha256: string | null;
  sizeBytes: number | null;
  uploadedBy: string;
  uploadedAt: string;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export function useFetchPayloadVersions(
  containerAppId: string,
  fileName: string,
) {
  const versions = ref<PayloadVersionIO[]>([]);
  const isLoading = ref(false);
  const error = ref<string | null>(null);

  async function load() {
    isLoading.value = true;
    error.value = null;

    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) {
      error.value = "Not authenticated";
      isLoading.value = false;
      return;
    }

    const url =
      `${v2BaseUrl()}/v2/file-containers/` +
      `${encodeURIComponent(containerAppId)}/files/` +
      `${encodeURIComponent(fileName)}/versions`;

    try {
      const resp = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (!resp.ok) {
        const body = await resp.text().catch(() => "");
        error.value = `Failed to load version history (HTTP ${resp.status})`;
        handleError(error.value + (body ? `: ${body.slice(0, 120)}` : ""), "fetchPayloadVersions");
        return;
      }
      versions.value = (await resp.json()) as PayloadVersionIO[];
    } catch (err) {
      error.value = err instanceof Error ? err.message : "Network error";
      handleError(error.value, "fetchPayloadVersions");
    } finally {
      isLoading.value = false;
    }
  }

  return { versions, isLoading, error, load };
}
