/**
 * MISSING-aas-ui Slice 6 — composable wrapping the AAS registry outbox endpoints.
 *
 * GET  /v2/admin/aas/registrations       — list all outbox rows (paged)
 * POST /v2/admin/aas/registrations/sync  — trigger on-demand sync
 *
 * Both endpoints require the instance-admin role.
 * On 501 the plugin is disabled; caller should guard with isPluginEnabled("aas").
 */

export type RegistrationStatus = "PENDING" | "SYNCED" | "FAILED";

export interface AasRegistrationIO {
  appId: string;
  shellAppId: string;
  registryUrl: string;
  status: RegistrationStatus;
  lastAttemptAt: number | null;
  errorMessage: string | null;
  createdAt: number;
  updatedAt: number;
}

export interface AasSyncResultIO {
  synced: number;
}

export interface AasRegistrationsPage {
  items: AasRegistrationIO[];
  total: number;
  page: number;
  pageSize: number;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export function useAasRegistrations() {
  const registrationsPage = ref<AasRegistrationsPage | null>(null);
  const isLoading = ref(false);
  const isSyncing = ref(false);
  const error = ref<string | null>(null);
  const lastSyncResult = ref<AasSyncResultIO | null>(null);

  async function load(pageIndex = 0, pageSize = 50) {
    isLoading.value = true;
    error.value = null;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const url = `${v2BaseUrl()}/v2/admin/aas/registrations?page=${pageIndex}&pageSize=${pageSize}`;
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      registrationsPage.value = (await response.json()) as AasRegistrationsPage;
    } catch (e) {
      error.value = "Failed to load AAS registrations";
      handleError(e, "fetching AAS registrations");
    } finally {
      isLoading.value = false;
    }
  }

  async function triggerSync(): Promise<AasSyncResultIO | null> {
    isSyncing.value = true;
    error.value = null;
    lastSyncResult.value = null;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const response = await fetch(
        `${v2BaseUrl()}/v2/admin/aas/registrations/sync`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${accessToken}`,
            Accept: "application/json",
          },
        },
      );
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const result = (await response.json()) as AasSyncResultIO;
      lastSyncResult.value = result;
      await load();
      return result;
    } catch (e) {
      error.value = "Registry sync failed";
      handleError(e, "triggering AAS registry sync");
      return null;
    } finally {
      isSyncing.value = false;
    }
  }

  load();

  return { registrationsPage, isLoading, isSyncing, error, lastSyncResult, load, triggerSync };
}
