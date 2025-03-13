import type {
  Permissions,
  PermissionType,
  ResponseError,
  User,
} from "@dlr-shepard/backend-client";
import { CollectionApi, UserApi } from "@dlr-shepard/backend-client";

export function useFetchCollectionPermissions(collectionId: number) {
  const collectionPermissions = ref<Permissions | undefined>(undefined);
  const owner = ref<User | undefined>(undefined);
  const permissionType = ref<PermissionType | undefined>(undefined);

  async function fetchCollectionPermissions(collectionId: number) {
    await createApiInstance(CollectionApi)
      .getCollectionPermissions({ collectionId })
      .then(response => {
        collectionPermissions.value = response;
        getUserDetails(response.owner);
        permissionType.value = response.permissionType;
      })
      .catch(e => {
        handleError(e as ResponseError, "fetching collection permissions");
      });
  }

  fetchCollectionPermissions(collectionId);

  async function getUserDetails(username: string | undefined) {
    if (!username) return;
    const userDetails = await createApiInstance(UserApi).getUser({ username });
    if (userDetails) {
      owner.value = userDetails;
    }
  }

  return {
    collectionPermissions,
    owner,
  };
}
