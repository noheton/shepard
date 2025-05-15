import {
  StructuredDataContainerApi,
  type PermissionType,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

export async function useCreateStructuredDataContainer(
  structuredDataContainerName: string,
  permissionType: PermissionType,
) {
  const api = useShepardApi(StructuredDataContainerApi);

  const newStructuredDataContainer = await api.value
    .createStructuredDataContainer({
      structuredDataContainer: { name: structuredDataContainerName },
    })
    .then(response => {
      return response;
    })
    .catch(error => {
      handleError(error, "createStructuredDataContainer");
      return undefined;
    });
  if (!newStructuredDataContainer) return;

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
