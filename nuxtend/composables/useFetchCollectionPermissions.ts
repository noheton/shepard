import type { Permissions, ResponseError } from "@dlr-shepard/backend-client";
import { CollectionApi } from "@dlr-shepard/backend-client";

export function useFetchCollectionPermissions(collectionId: number) {
  const collectionPermissions = ref<Permissions | undefined>(undefined);

  async function fetchCollectionPermissions(collectionId: number) {
    await createApiInstance(CollectionApi)
      .getCollectionPermissions({ collectionId })
      .then(response => {
        collectionPermissions.value = response;
      })
      .catch(e => {
        handleError(e as ResponseError, "fetching collection permissions");
      });
  }

  fetchCollectionPermissions(collectionId);

  return {
    collectionPermissions,
  };
}
