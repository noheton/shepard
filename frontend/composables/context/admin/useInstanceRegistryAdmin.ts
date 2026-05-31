/**
 * FE-PROV-INSTANCE-REGISTRY admin composable wrapping
 * GET / PATCH /v2/admin/instances.
 *
 * Pairs with useInstanceRegistry (the lightweight badge-resolver). This
 * composable adds the admin-only mutate path and is the backing store
 * for the AdminInstanceRegistryPane.
 *
 * Wire shape:
 *   GET   -> { instances: RegisteredInstance[] }
 *   PATCH -> body { instances: RegisteredInstance[] | null }
 *
 * RFC 7396 atomic-array semantics: sending the full list replaces it.
 * To delete a row, re-PATCH with the row omitted.
 */

export interface RegisteredInstance {
  instanceId: string;
  displayName?: string | null;
  baseUrl?: string | null;
  dlrInstitute?: string | null;
}

export interface InstanceRegistryIO {
  instances: RegisteredInstance[];
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

const INSTANCE_REGISTRY_URL = "/v2/admin/instances";

export function useInstanceRegistryAdmin() {
  const instances = ref<RegisteredInstance[]>([]);
  const isLoading = ref(false);
  const isSaving = ref(false);
  const error = ref<string | null>(null);

  async function refresh() {
    isLoading.value = true;
    error.value = null;
    try {
      const headers: Record<string, string> = { Accept: "application/json" };
      // GET is public; Authorization optional.
      try {
        const { data: session } = useAuth();
        const accessToken = session.value?.accessToken;
        if (accessToken) headers["Authorization"] = `Bearer ${accessToken}`;
      } catch {
        // ignore — composable may run outside an auth context in tests.
      }
      const response = await fetch(
        `${v2BaseUrl()}${INSTANCE_REGISTRY_URL}`,
        { headers },
      );
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const body = (await response.json()) as InstanceRegistryIO;
      instances.value = body.instances ?? [];
    } catch (e) {
      error.value = "Failed to load instance registry";
      handleError(e, "fetching instance registry");
    } finally {
      isLoading.value = false;
    }
  }

  async function replaceInstances(
    next: RegisteredInstance[],
  ): Promise<InstanceRegistryIO | null> {
    isSaving.value = true;
    error.value = null;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const response = await fetch(
        `${v2BaseUrl()}${INSTANCE_REGISTRY_URL}`,
        {
          method: "PATCH",
          headers: {
            Authorization: `Bearer ${accessToken}`,
            "Content-Type": "application/json",
            Accept: "application/json",
          },
          body: JSON.stringify({ instances: next }),
        },
      );
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
      const updated = (await response.json()) as InstanceRegistryIO;
      instances.value = updated.instances ?? [];
      return updated;
    } catch (e) {
      error.value = "Failed to save instance registry";
      handleError(e, "patching instance registry");
      return null;
    } finally {
      isSaving.value = false;
    }
  }

  async function addInstance(
    next: RegisteredInstance,
  ): Promise<InstanceRegistryIO | null> {
    // RFC 7396 array semantics: send the full list with the new row appended.
    // De-dup by instanceId — replace if present rather than appending a duplicate.
    const withoutDup = instances.value.filter(
      (i) => i.instanceId !== next.instanceId,
    );
    return replaceInstances([...withoutDup, next]);
  }

  async function deleteInstance(
    instanceId: string,
  ): Promise<InstanceRegistryIO | null> {
    const filtered = instances.value.filter((i) => i.instanceId !== instanceId);
    return replaceInstances(filtered);
  }

  refresh();

  return {
    instances,
    isLoading,
    isSaving,
    error,
    refresh,
    replaceInstances,
    addInstance,
    deleteInstance,
  };
}
