import {
  StructuredDataContainerApi,
  type PermissionType,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";
import { createV2Container } from "../container/createV2Container";

/**
 * V2CONV-A3: container creation now goes through the unified
 * `POST /v2/containers?kind=structured-data` surface (hand-written fetch via
 * {@link createV2Container}; the generated client has no ContainersV2Api yet).
 * The permission setup still uses the v1 StructuredDataContainerApi keyed by
 * the numeric `id` resolved from the created v2 container — there is no `/v2/`
 * container-permission endpoint yet (CONTAINER-PERMS-V2 in aidocs/16).
 */
export async function useCreateStructuredDataContainer(
  structuredDataContainerName: string,
  permissionType: PermissionType,
) {
  const newStructuredDataContainer = await createV2Container(
    "structured-data",
    structuredDataContainerName,
  );
  if (!newStructuredDataContainer) return;

  const api = useShepardApi(StructuredDataContainerApi);

  const currentPermissions = await api.value
    .getStructuredDataPermissions({
      structuredDataContainerId: newStructuredDataContainer.id,
    })
    .then(response => {
      return response;
    })
    .catch(error => {
      handleError(error, "getPermissions");
      return undefined;
    });
  if (!currentPermissions) return;

  const permissionsUpdateSuccess = await api.value
    .editStructuredDataPermissions({
      structuredDataContainerId: newStructuredDataContainer.id,
      permissions: {
        ...currentPermissions,
        permissionType: permissionType,
      },
    })
    .then(_ => {
      return true;
    })
    .catch(error => {
      handleError(error, "updatePermissions");
      return false;
    });
  if (!permissionsUpdateSuccess) return;

  emitSuccess(
    `Successfully created container "${newStructuredDataContainer.name}"`,
  );

  return newStructuredDataContainer;
}
