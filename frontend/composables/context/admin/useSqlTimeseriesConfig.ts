/**
 * P10c composable wrapping GET/PATCH /v2/admin/config/sql-timeseries
 * (V2CONV-A4 generic admin-config surface; was /v2/admin/sql-timeseries/config).
 *
 * Raw fetch (no generated client for this endpoint yet) — same pattern as
 * useUnhideAdminConfig and useInstanceRorConfig.
 *
 * Wire shape (always fully-resolved, never sparse):
 *   { maxRows: number, maxDuration: string }
 *
 * PATCH semantics (RFC 7396):
 *   absent field → leave current value
 *   null         → clear field (revert to deploy-time default)
 *   value        → replace
 */

export interface SqlTimeseriesConfigIO {
  maxRows: number;
  maxDuration: string;
}

/** Subset used in PATCH body — each field optional; null = clear to default. */
export interface SqlTimeseriesConfigPatch {
  maxRows?: number | null;
  maxDuration?: string | null;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

const SQL_TS_CONFIG_URL = "/v2/admin/config/sql-timeseries";

export function useSqlTimeseriesConfig() {
  const config = ref<SqlTimeseriesConfigIO | null>(null);
  const isLoading = ref(false);
  const isSaving = ref(false);
  const error = ref<string | null>(null);

  async function refresh() {
    isLoading.value = true;
    error.value = null;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const response = await fetch(`${v2BaseUrl()}${SQL_TS_CONFIG_URL}`, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      config.value = (await response.json()) as SqlTimeseriesConfigIO;
    } catch (e) {
      error.value = "Failed to load SQL timeseries config";
      handleError(e, "fetching SQL timeseries config");
    } finally {
      isLoading.value = false;
    }
  }

  async function patch(
    updates: SqlTimeseriesConfigPatch,
  ): Promise<SqlTimeseriesConfigIO | null> {
    isSaving.value = true;
    error.value = null;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const response = await fetch(`${v2BaseUrl()}${SQL_TS_CONFIG_URL}`, {
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
          // ignore parse errors — keep generic message
        }
        error.value = detail;
        return null;
      }
      const updated = (await response.json()) as SqlTimeseriesConfigIO;
      config.value = updated;
      return updated;
    } catch (e) {
      error.value = "Failed to save SQL timeseries config";
      handleError(e, "patching SQL timeseries config");
      return null;
    } finally {
      isSaving.value = false;
    }
  }

  refresh();

  return { config, isLoading, isSaving, error, refresh, patch };
}
