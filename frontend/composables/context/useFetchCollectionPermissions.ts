import type {
  Permissions,
  PermissionType,
  ResponseError,
  User,
} from "@dlr-shepard/backend-client";
import { CollectionPermissionsApi, UserApi } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";

/**
 * BUG-COLL-APPID-ROUTE-PERMS-1: migrated from v1 `getCollectionPermissions({collectionId: number})`
 * to v2 `getCollectionPermissions({appId: string})`. The v1 endpoint required a numeric Neo4j OGM
 * id, which is absent on post-reset Collections (UUID v7 only). The v2 endpoint is keyed by appId.
 */
export function useFetchCollectionPermissions(collectionAppId: string) {
  const collectionPermissions = ref<Permissions | undefined>(undefined);
  const owner = ref<User | undefined>(undefined);
  const permissionType = ref<PermissionType | undefined>(undefined);

  async function fetchCollectionPermissions(appId: string) {
    await useV2ShepardApi(CollectionPermissionsApi)
      .value.getCollectionPermissions({ appId })
      .then(response => {
        collectionPermissions.value = response;
        getUserDetails(response.owner);
        permissionType.value = response.permissionType;
      })
      .catch(e => {
        handleError(e as ResponseError, "fetching collection permissions");
      });
  }

  fetchCollectionPermissions(collectionAppId);

  async function getUserDetails(username: string | undefined) {
    if (!username) return;
    const userDetails = await useShepardApi(UserApi).value.getUser({
      username,
    });
    if (userDetails) {
      owner.value = userDetails;
    }
  }

  return {
    collectionPermissions,
    owner,
  };
}
