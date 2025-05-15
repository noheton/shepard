import {
  SpatialDataContainerApi,
  type PermissionType,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

export async function useCreateSpatialDataContainer(
  fileContainerName: string,
  permissionType: PermissionType,
) {
  const api = useShepardApi(SpatialDataContainerApi);

  const newSpatialDataContainer = await api.value
    .createSpatialDataContainer({
      spatialDataContainer: { name: fileContainerName },
    })
    .then(response => {
      return response;
    })
    .catch(error => {
      handleError(error, "createFileContainer");
      return undefined;
    });
  if (!newSpatialDataContainer) return;

  const currentPermissions = await api.value
    .getSpatialDataPermissions({
      spatialDataContainerId: newSpatialDataContainer.id,
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
    .editSpatialDataPermissions({
      spatialDataContainerId: newSpatialDataContainer.id,
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
    `Successfully created container "${newSpatialDataContainer.name}"`,
  );

  return newSpatialDataContainer;
}
