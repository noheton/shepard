/**
 * MISSING-aas-ui Slice 3 — composable wrapping GET/PATCH /v2/admin/config/aas.
 *
 * Follows the same pattern as useUnhideAdminConfig and useSemanticConfig.
 * The `registryApiKey` field is write-only; the GET response surfaces only
 * `apiKeyPresent` (boolean).
 */

export interface AasConfigIO {
  enabled: boolean;
  registryUrl?: string | null;
  apiKeyPresent: boolean;
  baseUrl?: string | null;
}

export interface AasConfigPatch {
  enabled?: boolean;
  registryUrl?: string | null;
  registryApiKey?: string | null;
  baseUrl?: string | null;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export function useAasAdminConfig() {
  const config = ref<AasConfigIO | null>(null);
  const isLoading = ref(false);
  const isSaving = ref(false);
  const error = ref<string | null>(null);

  async function refresh() {
    isLoading.value = true;
    error.value = null;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const response = await fetch(`${v2BaseUrl()}/v2/admin/config/aas`, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      config.value = (await response.json()) as AasConfigIO;
    } catch (e) {
      error.value = "Failed to load AAS config";
      handleError(e, "fetching AAS admin config");
    } finally {
      isLoading.value = false;
    }
  }

  async function patch(updates: AasConfigPatch): Promise<AasConfigIO | null> {
    isSaving.value = true;
    error.value = null;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const response = await fetch(`${v2BaseUrl()}/v2/admin/config/aas`, {
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
      const updated = (await response.json()) as AasConfigIO;
      config.value = updated;
      return updated;
    } catch (e) {
      error.value = "Failed to save AAS config";
      handleError(e, "patching AAS admin config");
      return null;
    } finally {
      isSaving.value = false;
    }
  }

  refresh();

  return { config, isLoading, isSaving, error, refresh, patch };
}
