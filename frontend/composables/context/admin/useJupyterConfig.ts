/**
 * J1e composable wrapping GET/PATCH /v2/admin/config/jupyter (admin
 * write surface; V2CONV-A4 generic admin-config surface, was
 * /v2/admin/jupyter/config) and GET /v2/config/jupyter (public read surface,
 * APISIMP-JUPYTER-PUBLIC-CONFIG-GENERIC — was /v2/jupyter/config, now 301-redirects).
 *
 * Raw fetch (no generated client for this endpoint yet) — same pattern as
 * useSqlTimeseriesConfig and useInstanceRorConfig.
 *
 * Wire shape:
 *   { enabled: boolean, hubUrl: string | null }
 *
 * PATCH semantics (RFC 7396):
 *   absent field   → leave current value
 *   null on hubUrl → clear to deploy-time default
 *   value          → replace
 *
 * The "Open in JupyterHub" affordance is visible to users only when both
 * knobs resolve truthy: `enabled === true && hubUrl != null && hubUrl !== ""`.
 */

export interface JupyterConfigIO {
  enabled: boolean;
  hubUrl: string | null;
}

/** Subset used in PATCH body — each field optional; null hubUrl = clear. */
export interface JupyterConfigPatch {
  enabled?: boolean | null;
  hubUrl?: string | null;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

// Admin (instance-admin-only) endpoint — used for GET when the admin
// tile is open (so a 403 surfaces for non-admins) and for every PATCH.
const JUPYTER_ADMIN_CONFIG_URL = "/v2/admin/config/jupyter";

// Public (any authenticated user) endpoint — used by the unified data-
// references table to decide whether to render the "Open in JupyterHub"
// action. Returns the same JupyterConfigIO shape.
// Canonical: /v2/config/jupyter (generic public-read surface, APISIMP-JUPYTER-PUBLIC-CONFIG-GENERIC).
// The old /v2/jupyter/config path now 301-redirects here.
const JUPYTER_PUBLIC_CONFIG_URL = "/v2/config/jupyter";

/**
 * @param options.adminMode when true, READS go to the admin endpoint
 *   (`/v2/admin/config/jupyter`) — used by the admin tile. When false
 *   (default), reads go to the public endpoint (`/v2/config/jupyter`)
 *   — used by the unified data-references table for every authenticated
 *   user. PATCH always targets the admin endpoint (instance-admin-gated
 *   by the backend).
 */
export function useJupyterConfig(options?: { adminMode?: boolean }) {
  const adminMode = options?.adminMode === true;
  const readUrl = adminMode ? JUPYTER_ADMIN_CONFIG_URL : JUPYTER_PUBLIC_CONFIG_URL;
  const config = ref<JupyterConfigIO | null>(null);
  const isLoading = ref(false);
  const isSaving = ref(false);
  const error = ref<string | null>(null);

  async function refresh() {
    isLoading.value = true;
    error.value = null;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const response = await fetch(`${v2BaseUrl()}${readUrl}`, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      config.value = (await response.json()) as JupyterConfigIO;
    } catch (e) {
      // Public read is a fail-soft probe — the "Open in JupyterHub" affordance
      // simply stays hidden when the config can't be read. Surfacing a toast
      // here would pollute every DataObject-detail page (the unified data-
      // references table calls this on mount). Only the admin tile, which
      // explicitly opened the config pane, gets an error toast.
      config.value = null;
      if (adminMode) {
        error.value = "Failed to load Jupyter config";
        handleError(e, "fetching Jupyter config");
      }
    } finally {
      isLoading.value = false;
    }
  }

  async function patch(
    updates: JupyterConfigPatch,
  ): Promise<JupyterConfigIO | null> {
    isSaving.value = true;
    error.value = null;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const response = await fetch(`${v2BaseUrl()}${JUPYTER_ADMIN_CONFIG_URL}`, {
        method: "PATCH",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/merge-patch+json",
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
      const updated = (await response.json()) as JupyterConfigIO;
      config.value = updated;
      return updated;
    } catch (e) {
      error.value = "Failed to save Jupyter config";
      handleError(e, "patching Jupyter config");
      return null;
    } finally {
      isSaving.value = false;
    }
  }

  refresh();

  return { config, isLoading, isSaving, error, refresh, patch };
}
