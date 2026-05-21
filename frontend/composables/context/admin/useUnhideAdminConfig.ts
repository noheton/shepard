/**
 * UH1d composable wrapping GET/PATCH /v2/admin/unhide/config.
 *
 * Raw fetch (no generated client for this endpoint yet) — same pattern
 * as useInstanceRorConfig and useFetchPlugins.
 */

export interface UnhideConfigIO {
  enabled: boolean;
  feedPublic: boolean;
  contactEmail?: string | null;
  harvestApiKeyMintedAt?: string | null;
  harvestApiKeyFingerprint?: string | null;
}

export interface UnhideConfigPatch {
  enabled?: boolean;
  feedPublic?: boolean;
  contactEmail?: string | null;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export function useUnhideAdminConfig() {
  const config = ref<UnhideConfigIO | null>(null);
  const isLoading = ref(false);
  const isSaving = ref(false);
  const error = ref<string | null>(null);

  async function refresh() {
    isLoading.value = true;
    error.value = null;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const response = await fetch(`${v2BaseUrl()}/v2/admin/unhide/config`, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      config.value = (await response.json()) as UnhideConfigIO;
    } catch (e) {
      error.value = "Failed to load Unhide config";
      handleError(e, "fetching Unhide config");
    } finally {
      isLoading.value = false;
    }
  }

  async function patch(updates: UnhideConfigPatch): Promise<UnhideConfigIO | null> {
    isSaving.value = true;
    error.value = null;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const response = await fetch(`${v2BaseUrl()}/v2/admin/unhide/config`, {
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
      const updated = (await response.json()) as UnhideConfigIO;
      config.value = updated;
      return updated;
    } catch (e) {
      error.value = "Failed to save Unhide config";
      handleError(e, "patching Unhide config");
      return null;
    } finally {
      isSaving.value = false;
    }
  }

  refresh();

  return { config, isLoading, isSaving, error, refresh, patch };
}
