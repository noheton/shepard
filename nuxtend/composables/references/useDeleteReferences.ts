import {
  CollectionReferenceApi,
  DataObjectReferenceApi,
  UriReferenceApi,
} from "@dlr-shepard/backend-client";

export function useDeleteReferences(
  collectionId: number,
  dataObjectId: number,
  onSuccess: () => void,
  isLoading?: Ref<boolean>,
) {
  const loading = isLoading ?? ref<boolean>(false);

  async function deleteUriReference(uriReferenceId: number) {
    loading.value = true;

    createApiInstance(UriReferenceApi)
      .deleteUriReference({
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

    createApiInstance(CollectionReferenceApi)
      .deleteCollectionReference({
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

    createApiInstance(DataObjectReferenceApi)
      .deleteDataObjectReference({
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
    loading,
  };
}
