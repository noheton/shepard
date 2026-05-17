import { MeApi } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";

const PREF_KEY = "editor.preferredJupyter";

export function useJupyterPreference() {
  const preferredJupyterUrl = ref<string>("");
  const isSaving = ref(false);

  async function load() {
    try {
      const prefs = await useV2ShepardApi(MeApi).value.getPreferences();
      preferredJupyterUrl.value = prefs[PREF_KEY] ?? "";
    } catch (error) {
      handleError(error, "loading Jupyter preference");
    }
  }

  async function save(url: string) {
    isSaving.value = true;
    try {
      await useV2ShepardApi(MeApi).value.patchPreferences({ [PREF_KEY]: url });
      preferredJupyterUrl.value = url;
    } catch (error) {
      handleError(error, "saving Jupyter preference");
    } finally {
      isSaving.value = false;
    }
  }

  load();
  return { preferredJupyterUrl, isSaving, save };
}
