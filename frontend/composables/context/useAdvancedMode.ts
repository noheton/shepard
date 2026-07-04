import { MeApi } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";
import { handleError } from "~/utils/errorBus";

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
      // V2-SWEEP-001-CLIENT-REGEN: getPreferences now returns `object`; the
      // preferences map is a flat string→string bag keyed by PREF_KEY.
      const prefs = (await api.value.getPreferences()) as Record<string, string>;
      advancedMode.value = prefs[PREF_KEY] === "true";
    } catch {
      advancedMode.value = false;
    }
  }

  async function setAdvancedMode(enabled: boolean) {
    // Optimistic toggle: flip the UI immediately, revert on error.
    // The user reported the switch "spinning infinitely" when the
    // patch returns 4xx — because the upstream client throws but
    // the spinner state was clearing in `finally`, so the spin was
    // really from Vuetify's v-switch in `loading` mode locking the
    // visual until isSaving flips false. Explicit revert + an
    // emergency timeout (5s) guarantee the switch becomes interactive
    // again even when the response path stalls (slow network, CORS
    // preflight retry, etc.).
    const previous = advancedMode.value;
    advancedMode.value = enabled;
    isSaving.value = true;
    const safety = setTimeout(() => {
      isSaving.value = false;
    }, 5000);
    try {
      await api.value.patchPreferences({ body: { [PREF_KEY]: enabled ? "true" : "false" } });
    } catch (error) {
      advancedMode.value = previous; // revert
      handleError(error, "saving advanced mode preference");
    } finally {
      clearTimeout(safety);
      isSaving.value = false;
    }
  }

  function toggleAdvancedMode() {
    return setAdvancedMode(!advancedMode.value);
  }

  load();
  return { advancedMode, isSaving, setAdvancedMode, toggleAdvancedMode };
}
