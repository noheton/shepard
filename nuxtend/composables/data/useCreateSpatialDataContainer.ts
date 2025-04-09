import {
  SpatialDataContainerApi,
  type PermissionType,
} from "@dlr-shepard/backend-client";

export async function useCreateSpatialDataContainer(
  fileContainerName: string,
  permissionType: PermissionType,
) {
  const api = createApiInstance(SpatialDataContainerApi);

  const newSpatialDataContainer = await api
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

  const currentPermissions = await api
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

  const permissionsUpdateSuccess = await api
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
