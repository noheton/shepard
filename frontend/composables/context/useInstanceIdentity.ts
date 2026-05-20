import { ref, type Ref } from "vue";

export interface InstanceIdentity {
  rorId?: string | null;
  organizationName?: string | null;
  rorUrl?: string | null;
}

const _identity = ref<InstanceIdentity | null>(null);
const _loaded = ref(false);

export function useInstanceIdentity(): {
  identity: Ref<InstanceIdentity | null>;
  loaded: Ref<boolean>;
  fetch: (accessToken: string) => Promise<void>;
} {
  async function fetch(accessToken: string) {
    if (_loaded.value) return;
    try {
      const config = useRuntimeConfig().public;
      const base = ((config as { backendV2ApiUrl?: string }).backendV2ApiUrl || "")
        || (config.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "").replace(/\/$/, "");
      const res = await globalThis.fetch(`${base}/v2/instance/identity`, {
        headers: { Authorization: `Bearer ${accessToken}`, Accept: "application/json" },
      });
      if (res.ok) _identity.value = (await res.json()) as InstanceIdentity;
    } catch {
      // non-fatal; identity stays null → header shows nothing extra
    } finally {
      _loaded.value = true;
    }
  }

  return { identity: _identity, loaded: _loaded, fetch };
}
