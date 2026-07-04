import {
  CollectionReferenceApi,
  DataObjectReferenceApi,
  UriReferenceApi,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export function useDeleteReferences(
  collectionId: number,
  dataObjectId: number,
  onSuccess: () => void,
  isLoading?: Ref<boolean>,
) {
  const loading = isLoading ?? ref<boolean>(false);

  // V2-SWEEP-004-3: v2 delete path for URI references; uses the reference's own appId.
  async function deleteUriReferenceV2(appId: string) {
    loading.value = true;
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    await fetch(`${v2BaseUrl()}/v2/references/${encodeURIComponent(appId)}`, {
      method: "DELETE",
      headers: { ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}) },
    }).catch(error => {
      handleError(error, "deleteUriReferenceV2");
    });
    emitSuccess("Successfully deleted URI reference");
    handleDataObjectUpdate();
    onSuccess();
    loading.value = false;
  }

  async function deleteUriReference(uriReferenceId: number) {
    loading.value = true;

    useShepardApi(UriReferenceApi)
      .value.deleteUriReference({
        collectionId,
        dataObjectId,
        uriReferenceId,
      })
      .then(_ => {
        emitSuccess("Successfully deleted URI reference");
        handleDataObjectUpdate();
        onSuccess();
      })
      .catch(error => {
        handleError(error, "deleteUriReference");
      });
    loading.value = false;
  }

  async function deleteCollectionReference(collectionReferenceId: number) {
    loading.value = true;

    useShepardApi(CollectionReferenceApi)
      .value.deleteCollectionReference({
        collectionId,
        dataObjectId,
        collectionReferenceId,
      })
      .then(_ => {
        emitSuccess("Successfully deleted Collection reference");
        handleDataObjectUpdate();
        onSuccess();
      })
      .catch(error => {
        handleError(error, "deleteCollectionReference");
      });
    loading.value = false;
  }

  async function deleteDataObjectReference(dataObjectReferenceId: number) {
    loading.value = true;

    useShepardApi(DataObjectReferenceApi)
      .value.deleteDataObjectReference({
        collectionId,
        dataObjectId,
        dataObjectReferenceId,
      })
      .then(_ => {
        emitSuccess("Successfully deleted Data Object reference");
        handleDataObjectUpdate();
        onSuccess();
      })
      .catch(error => {
        handleError(error, "deleteDataObjectReference");
      });
    loading.value = false;
  }

  return {
    deleteCollectionReference,
    deleteDataObjectReference,
    deleteUriReference,
    deleteUriReferenceV2,
    loading,
  };
}
