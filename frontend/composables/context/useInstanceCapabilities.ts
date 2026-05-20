import { ref, type Ref } from "vue";

export interface InstanceCapabilities {
  enabledPlugins: string[];
}

const _capabilities = ref<InstanceCapabilities | null>(null);
const _loaded = ref(false);

export function useInstanceCapabilities(): {
  capabilities: Ref<InstanceCapabilities | null>;
  loaded: Ref<boolean>;
  fetch: (accessToken: string) => Promise<void>;
  isPluginEnabled: (id: string) => boolean;
} {
  async function fetch(accessToken: string) {
    if (_loaded.value) return;
    try {
      const config = useRuntimeConfig().public;
      const base = ((config as { backendV2ApiUrl?: string }).backendV2ApiUrl || "")
        || (config.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "").replace(/\/$/, "");
      const res = await globalThis.fetch(`${base}/v2/instance/capabilities`, {
        headers: { Authorization: `Bearer ${accessToken}`, Accept: "application/json" },
      });
      if (res.ok) _capabilities.value = (await res.json()) as InstanceCapabilities;
    } catch {
      // non-fatal; capabilities stays null → all plugin panels hidden until loaded
    } finally {
      _loaded.value = true;
    }
  }

  function isPluginEnabled(id: string): boolean {
    return _capabilities.value?.enabledPlugins.includes(id) ?? false;
  }

  return { capabilities: _capabilities, loaded: _loaded, fetch, isPluginEnabled };
}
