import type { Collection, ResponseError } from "@dlr-shepard/backend-client";
import { CollectionApi } from "@dlr-shepard/backend-client";

export function useCollection(collectionId?: number) {
  const collection = ref<Collection | undefined>(undefined);

  function fetchCollection(collectionId?: number) {
    if (!collectionId) return;
    createApiInstance(CollectionApi)
      .getCollection({ collectionId })
      .then(response => {
        collection.value = response;
      })
      .catch(e => {
        handleError(e as ResponseError, "fetching collection");
      });
  }

  fetchCollection(collectionId);

  return { collection, fetchCollection };
}
