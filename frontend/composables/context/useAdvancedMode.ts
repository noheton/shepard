import { MeApi } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";

const PREF_KEY = "ui.advancedMode";

// Module-level singleton so all callers share one API fetch.
const advancedMode = ref(false);
const isSaving = ref(false);
let loaded = false;

export function useAdvancedMode() {
  const api = useV2ShepardApi(MeApi);

  async function load() {
    if (loaded) return;
    loaded = true;
    try {
      const prefs = await api.value.getPreferences();
      advancedMode.value = prefs[PREF_KEY] === "true";
    } catch {
      advancedMode.value = false;
    }
  }

  async function setAdvancedMode(enabled: boolean) {
    isSaving.value = true;
    try {
      await api.value.patchPreferences({ [PREF_KEY]: enabled ? "true" : "false" });
      advancedMode.value = enabled;
    } catch (error) {
      handleError(error, "saving advanced mode preference");
    } finally {
      isSaving.value = false;
    }
  }

  load();
  return { advancedMode, isSaving, setAdvancedMode };
}
