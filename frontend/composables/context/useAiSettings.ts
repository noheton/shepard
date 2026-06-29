import { MeApi } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";
import { handleError } from "~/utils/errorBus";

const PREF_BASE_URL = "ai.baseUrl";
const PREF_MODEL = "ai.model";

// Module-level singletons so all callers share one API fetch.
const baseUrl = ref("");
const model = ref("");
const isSaving = ref(false);
let loaded = false;

export function useAiSettings() {
  const api = useV2ShepardApi(MeApi);

  async function load() {
    if (loaded) return;
    loaded = true;
    try {
      const prefs = (await api.value.getPreferences()) as Record<string, string>;
      baseUrl.value = prefs[PREF_BASE_URL] ?? "";
      model.value = prefs[PREF_MODEL] ?? "";
    } catch {
      // leave defaults; backend unreachable is not fatal here
    }
  }

  async function save(newBaseUrl: string, newModel: string) {
    const prevBaseUrl = baseUrl.value;
    const prevModel = model.value;
    baseUrl.value = newBaseUrl;
    model.value = newModel;
    isSaving.value = true;
    // Safety valve: never spin forever if the response never arrives.
    const safety = setTimeout(() => {
      isSaving.value = false;
    }, 5000);
    try {
      await api.value.patchPreferences({
        body: {
          // null = RFC 7396 remove; empty string cleared by the user means remove
          [PREF_BASE_URL]: newBaseUrl.trim() || null,
          [PREF_MODEL]: newModel.trim() || null,
        },
      });
    } catch (error) {
      baseUrl.value = prevBaseUrl;
      model.value = prevModel;
      handleError(error, "saving AI settings");
    } finally {
      clearTimeout(safety);
      isSaving.value = false;
    }
  }

  load();
  return { baseUrl, model, isSaving, save };
}
