import {
  GitCredentialsApi,
  type CreateGitCredentialIO,
  type GitCredentialIO,
  type PatchGitCredentialIO,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";

export function useManageGitCredentials() {
  const isSaving = ref<boolean>(false);
  const saveError = ref<string | null>(null);

  const api = useV2ShepardApi(GitCredentialsApi);

  async function create(body: CreateGitCredentialIO): Promise<GitCredentialIO | null> {
    isSaving.value = true;
    saveError.value = null;
    try {
      return await api.value.createCredential(body);
    } catch (error) {
      saveError.value = "Failed to create git credential.";
      handleError(error, "creating git credential");
      return null;
    } finally {
      isSaving.value = false;
    }
  }

  async function patch(appId: string, body: PatchGitCredentialIO): Promise<GitCredentialIO | null> {
    isSaving.value = true;
    saveError.value = null;
    try {
      return await api.value.patchCredential(appId, body);
    } catch (error) {
      saveError.value = "Failed to update git credential.";
      handleError(error, "updating git credential");
      return null;
    } finally {
      isSaving.value = false;
    }
  }

  async function remove(appId: string): Promise<boolean> {
    isSaving.value = true;
    saveError.value = null;
    try {
      await api.value.deleteCredential(appId);
      return true;
    } catch (error) {
      saveError.value = "Failed to delete git credential.";
      handleError(error, "deleting git credential");
      return false;
    } finally {
      isSaving.value = false;
    }
  }

  return { create, patch, remove, isSaving, saveError };
}
