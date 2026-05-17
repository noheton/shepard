/**
 * UH1d — composable wrapping GET/PATCH /v2/collections/{appId}/properties.
 *
 * Raw fetch rather than a generated client because there is no
 * CollectionPropertiesApi in @dlr-shepard/backend-client yet. When the
 * regenerated client ships one, swap this composable to use it.
 */

export interface CollectionPropertiesIO {
  appId: string;
  webdavVisible: boolean;
  defaultOntologyUri: string | null;
  uiDefaultsJson: string | null;
  publishToHelmholtzKG: boolean;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export function useCollectionProperties(collectionAppId: string) {
  const properties = ref<CollectionPropertiesIO | null>(null);
  const isLoading = ref(false);
  const isSaving = ref(false);
  const error = ref<string | null>(null);

  async function refresh() {
    isLoading.value = true;
    error.value = null;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const url = `${v2BaseUrl()}/v2/collections/${collectionAppId}/properties`;
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      properties.value = await response.json();
    } catch (e) {
      error.value = "Failed to load collection properties";
      handleError(e, "fetching collection properties");
    } finally {
      isLoading.value = false;
    }
  }

  async function patch(
    updates: Partial<Pick<CollectionPropertiesIO, "webdavVisible" | "publishToHelmholtzKG" | "defaultOntologyUri" | "uiDefaultsJson">>,
  ): Promise<CollectionPropertiesIO | null> {
    isSaving.value = true;
    error.value = null;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const url = `${v2BaseUrl()}/v2/collections/${collectionAppId}/properties`;
      const response = await fetch(url, {
        method: "PATCH",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify(updates),
      });
      if (!response.ok) {
        const bodyText = await response.text().catch(() => "");
        let detail = `PATCH failed (HTTP ${response.status})`;
        try {
          const parsed = JSON.parse(bodyText);
          if (parsed && typeof parsed.detail === "string") detail = parsed.detail;
          else if (parsed && typeof parsed.title === "string") detail = parsed.title;
        } catch {
          // ignore parse errors
        }
        error.value = detail;
        return null;
      }
      const updated: CollectionPropertiesIO = await response.json();
      properties.value = updated;
      return updated;
    } catch (e) {
      error.value = "Failed to save collection properties";
      handleError(e, "patching collection properties");
      return null;
    } finally {
      isSaving.value = false;
    }
  }

  refresh();

  return { properties, isLoading, isSaving, error, refresh, patch };
}
