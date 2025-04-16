import {
  CollectionReferenceApi,
  DataObjectReferenceApi,
  UriReferenceApi,
} from "@dlr-shepard/backend-client";

export function useCreateReferences(
  collectionId: number,
  dataObjectId: number,
  onSuccess: () => void,
  isLoading?: Ref<boolean>,
) {
  const loading = isLoading ?? ref<boolean>(false);

  async function addUriReference(
    uri: string,
    name: string,
    relationship?: string,
  ) {
    loading.value = true;

    if (!relationship) {
      relationship = "URI";
    }

    createApiInstance(UriReferenceApi)
      .createUriReference({
        collectionId,
        dataObjectId,
        uRIReference: {
          name,
          uri,
          relationship,
        },
      })
      .then(_ => {
        emitSuccess("Successfully created URI reference");
        handleDataObjectUpdate();
        onSuccess();
      })
      .catch(error => {
        handleError(error, "createUriReference");
      });
    loading.value = false;
  }

  async function addCollectionReference(
    referencedCollectionId: number,
    name: string,
    relationship: string | undefined,
  ) {
    loading.value = true;

    if (!relationship) {
      relationship = "Collection";
    }

    createApiInstance(CollectionReferenceApi)
      .createCollectionReference({
        collectionId,
        dataObjectId,
        collectionReference: {
          name,
          referencedCollectionId: referencedCollectionId,
          relationship,
        },
      })
      .then(_ => {
        emitSuccess("Successfully created Collection reference");
        handleDataObjectUpdate();
        onSuccess();
      })
      .catch(error => {
        handleError(error, "createCollectionReference");
      });
    loading.value = false;
  }

  async function addDataObjectReference(
    referencedDataObjectId: number,
    name: string,
    relationship: string | undefined,
  ) {
    loading.value = true;

    if (!relationship) {
      relationship = "Data Object";
    }

    createApiInstance(DataObjectReferenceApi)
      .createDataObjectReference({
        collectionId,
        dataObjectId,
        dataObjectReference: {
          name,
          referencedDataObjectId: referencedDataObjectId,
          relationship,
        },
      })
      .then(_ => {
        emitSuccess("Successfully created Data Object reference");
        handleDataObjectUpdate();
        onSuccess();
      })
      .catch(error => {
        handleError(error, "createDataObjectReference");
      });
    loading.value = false;
  }

  return {
    addCollectionReference,
    addDataObjectReference,
    addUriReference,
    loading,
  };
}
