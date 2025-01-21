import type { Collection, ResponseError } from "@dlr-shepard/backend-client";
import { CollectionApi } from "@dlr-shepard/backend-client";
import type { CollectionRouteParams } from "~/utils/collectionRouteParams";

export function useFetchCollection(collectionId?: number) {
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

export const useFetchCollectionOfRouteParams = (
  routeParams: Ref<CollectionRouteParams>,
) => {
  const { collection, fetchCollection } = useFetchCollection(
    routeParams.value.collectionId,
  );

  watch(routeParams, () => {
    if (
      routeParams.value.collectionId &&
      collection.value?.id !== routeParams.value.collectionId
    ) {
      fetchCollection(routeParams.value.collectionId);
    }
  });

  return { collection };
};
