import {
  GitReferenceApi,
  type CreateGitReferenceIO,
  type GitReferenceIO,
  type PatchGitReferenceIO,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";

export function useManageGitReferences() {
  const isSaving = ref(false);
  const saveError = ref<string | null>(null);

  async function create(
    dataObjectAppId: string,
    body: CreateGitReferenceIO,
  ): Promise<GitReferenceIO | null> {
    isSaving.value = true;
    saveError.value = null;
    try {
      return await useV2ShepardApi(GitReferenceApi)
        .value.createGitReference(dataObjectAppId, body);
    } catch (error) {
      handleError(error, "createGitReference");
      saveError.value = "Failed to create git reference";
      return null;
    } finally {
      isSaving.value = false;
    }
  }

  async function patch(
    dataObjectAppId: string,
    appId: string,
    body: PatchGitReferenceIO,
  ): Promise<GitReferenceIO | null> {
    isSaving.value = true;
    saveError.value = null;
    try {
      return await useV2ShepardApi(GitReferenceApi)
        .value.patchGitReference(dataObjectAppId, appId, body);
    } catch (error) {
      handleError(error, "patchGitReference");
      saveError.value = "Failed to update git reference";
      return null;
    } finally {
      isSaving.value = false;
    }
  }

  async function remove(
    dataObjectAppId: string,
    appId: string,
  ): Promise<boolean> {
    isSaving.value = true;
    saveError.value = null;
    try {
      await useV2ShepardApi(GitReferenceApi)
        .value.deleteGitReference(dataObjectAppId, appId);
      return true;
    } catch (error) {
      handleError(error, "deleteGitReference");
      saveError.value = "Failed to delete git reference";
      return false;
    } finally {
      isSaving.value = false;
    }
  }

  return { create, patch, remove, isSaving, saveError };
}
