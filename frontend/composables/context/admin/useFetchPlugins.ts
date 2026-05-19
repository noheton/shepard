/**
 * PM1a–PM1e plugin admin composable.
 *
 * Uses raw fetch with Bearer auth because the plugin endpoints live under
 * /v2/admin/plugins and are NOT part of the generated backend-client.
 */

export interface PluginDependencyIO {
  pluginId: string;
  versionConstraint?: string | null;
}

export interface PluginEntryIO {
  id: string;
  version: string;
  shepardCompatibility?: string | null;
  state: "DISCOVERED" | "ENABLED" | "DISABLED" | "FAILED" | "DEGRADED";
  enabled: boolean;
  sourcePath?: string | null;
  registeredAt?: string | null;
  failureMessage?: string | null;
  title?: string | null;
  description?: string | null;
  homepageUrl?: string | null;
  repositoryUrl?: string | null;
  licence?: string | null;
  dependencies?: PluginDependencyIO[];
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

const STATE_ORDER: Record<PluginEntryIO["state"], number> = {
  FAILED: 0,
  DEGRADED: 1,
  ENABLED: 2,
  DISABLED: 3,
  DISCOVERED: 4,
};

export function useFetchPlugins() {
  const plugins = ref<PluginEntryIO[]>([]);
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

    const url = `${v2BaseUrl()}/v2/admin/plugins`;

    try {
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (!response.ok) {
        const bodyText = await response.text().catch(() => "");
        fetchError.value = `Failed to fetch plugins (HTTP ${response.status}): ${bodyText.slice(0, 200)}`;
        handleError(fetchError.value, "listPlugins");
        return;
      }
      const raw = (await response.json()) as PluginEntryIO[];
      plugins.value = [...raw].sort(
        (a, b) => STATE_ORDER[a.state] - STATE_ORDER[b.state],
      );
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Network error";
      fetchError.value = message;
      handleError(message, "listPlugins");
    } finally {
      isLoading.value = false;
    }
  }

  async function togglePlugin(id: string, enabled: boolean) {
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) {
      handleError("Not authenticated", "togglePlugin");
      return;
    }

    const url = `${v2BaseUrl()}/v2/admin/plugins/${encodeURIComponent(id)}`;

    const response = await fetch(url, {
      method: "PATCH",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify({ enabled }),
    });

    if (!response.ok) {
      const bodyText = await response.text().catch(() => "");
      throw new Error(
        `Failed to update plugin (HTTP ${response.status}): ${bodyText.slice(0, 200)}`,
      );
    }

    await refresh();
  }

  refresh();

  return { plugins, isLoading, fetchError, refresh, togglePlugin };
}
