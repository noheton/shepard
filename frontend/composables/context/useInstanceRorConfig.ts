/**
 * ROR1 composable wrapping `GET/PATCH /v2/admin/instance/ror`.
 *
 * Raw `fetch` rather than a generated client because the
 * `@dlr-shepard/backend-client` regeneration for the ROR1 admin
 * endpoints hasn't landed yet. When the regenerated client ships an
 * `InstanceRorApi`, swap this composable to use it — the wire shape is
 * the same.
 */

export interface InstanceRorConfigIO {
  rorId?: string | null;
  organizationName?: string | null;
  rorUrl?: string | null;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export function useInstanceRorConfig() {
  const config = ref<InstanceRorConfigIO | null>(null);
  const isLoading = ref(false);
  const error = ref<string | null>(null);

  async function refresh() {
    isLoading.value = true;
    error.value = null;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const url = `${v2BaseUrl()}/v2/admin/instance/ror`;
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      config.value = await response.json();
    } catch (e) {
      error.value = "Failed to load ROR config";
      handleError(e, "fetching ROR config");
    } finally {
      isLoading.value = false;
    }
  }

  async function patch(
    rorId: string | null | undefined,
    organizationName: string | null | undefined,
  ): Promise<InstanceRorConfigIO | null> {
    error.value = null;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const url = `${v2BaseUrl()}/v2/admin/instance/ror`;
      const body: Record<string, string | null> = {};
      if (rorId !== undefined) body.rorId = rorId;
      if (organizationName !== undefined) body.organizationName = organizationName;
      const response = await fetch(url, {
        method: "PATCH",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify(body),
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
      const updated: InstanceRorConfigIO = await response.json();
      config.value = updated;
      return updated;
    } catch (e) {
      error.value = "Failed to save ROR config";
      handleError(e, "patching ROR config");
      return null;
    }
  }

  refresh();

  return { config, isLoading, error, refresh, patch };
}
