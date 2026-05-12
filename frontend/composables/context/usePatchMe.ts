import { MeApi, type User } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";

export interface PatchMePayload {
  orcid?: string | null;
  displayName?: string | null;
}

export function usePatchMe() {
  const isSaving = ref<boolean>(false);
  const saveError = ref<string | null>(null);

  async function patchMe(payload: PatchMePayload): Promise<User | null> {
    isSaving.value = true;
    saveError.value = null;
    try {
      const result = await useV2ShepardApi(MeApi).value.patchMe({ body: payload });
      return result;
    } catch (error) {
      handleError(error, "updating profile");
      saveError.value = "Failed to save profile";
      return null;
    } finally {
      isSaving.value = false;
    }
  }

  return { patchMe, isSaving, saveError };
}
