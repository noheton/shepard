import {
  TimeseriesContainerApi,
  type PermissionType,
} from "@dlr-shepard/backend-client";

export async function useCreateTimeseriesContainer(
  timeseriesContainerName: string,
  permissionType: PermissionType,
) {
  const api = createApiInstance(TimeseriesContainerApi);

  const newTimeseriesContainer = await api
    .createTimeseriesContainer({
      timeseriesContainer: { name: timeseriesContainerName },
    })
    .then(response => {
      return response;
    })
    .catch(error => {
      handleError(error, "createTimeseriesContainer");
      return undefined;
    });
  if (!newTimeseriesContainer) return;

  const currentPermissions = await api
    .getTimeseriesPermissions({
      timeseriesContainerId: newTimeseriesContainer.id,
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
    .editTimeseriesPermissions({
      timeseriesContainerId: newTimeseriesContainer.id,
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
    `Successfully created container "${newTimeseriesContainer.name}"`,
  );

  return newTimeseriesContainer;
}
