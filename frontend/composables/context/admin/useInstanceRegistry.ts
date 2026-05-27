/**
 * FE-PROV-INSTANCE-REGISTRY — composable wrapping GET /v2/admin/instances.
 *
 * Fetches the operator-configured peer Shepard instance registry and exposes
 * a reactive Map keyed by instanceId → RegisteredInstance. Used by any badge
 * that resolves an instance ID to a friendly name (e.g. SourceInstanceBadge
 * hover text showing "DLR BT, Augsburg" instead of "dlr-augsburg").
 *
 * Auth posture: GET /v2/admin/instances is public (no JWT required), so this
 * composable intentionally omits the Authorization header to reflect the
 * intended posture and work before the user authenticates.
 *
 * Wire shape (from InstanceRegistryIO):
 *   { instances: RegisteredInstance[] }
 *
 * where RegisteredInstance = {
 *   instanceId: string;
 *   displayName?: string | null;
 *   baseUrl?: string | null;
 *   dlrInstitute?: string | null;
 * }
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

export function useInstanceRegistry() {
  /**
   * Reactive map of instanceId → RegisteredInstance. Empty map means the
   * registry hasn't loaded yet or has no entries. Falls back gracefully —
   * callers should check Map.has(instanceId) before rendering the tooltip.
   */
  const registryMap = ref<Map<string, RegisteredInstance>>(new Map());
  const isLoading = ref(false);
  const error = ref<string | null>(null);

  async function refresh() {
    isLoading.value = true;
    error.value = null;
    try {
      // Public endpoint — no Authorization header intentional.
      const response = await fetch(`${v2BaseUrl()}${INSTANCE_REGISTRY_URL}`, {
        headers: { Accept: "application/json" },
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const data = (await response.json()) as InstanceRegistryIO;
      const map = new Map<string, RegisteredInstance>();
      for (const inst of data.instances ?? []) {
        if (inst.instanceId) {
          map.set(inst.instanceId, inst);
        }
      }
      registryMap.value = map;
    } catch (e) {
      error.value = "Failed to load instance registry";
      handleError(e, "fetching instance registry");
    } finally {
      isLoading.value = false;
    }
  }

  refresh();

  return { registryMap, isLoading, error, refresh };
}
