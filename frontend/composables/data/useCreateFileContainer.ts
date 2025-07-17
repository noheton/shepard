import {
  FileContainerApi,
  type PermissionType,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

export async function useCreateFileContainer(
  fileContainerName: string,
  permissionType: PermissionType,
) {
  const api = useShepardApi(FileContainerApi);

  const newFileContainer = await api.value
    .createFileContainer({
      fileContainer: { name: fileContainerName },
    })
    .then(response => {
      return response;
    })
    .catch(error => {
      handleError(error, "createFileContainer");
      return undefined;
    });
  if (!newFileContainer) return;

  const currentPermissions = await api.value
    .getFilePermissions({ fileContainerId: newFileContainer.id })
    .then(response => {
      return response;
    })
    .catch(error => {
      handleError(error, "getPermissions");
      return undefined;
    });
  if (!currentPermissions) return;

  const permissionsUpdateSuccess = await api.value
    .editFilePermissions({
      fileContainerId: newFileContainer.id,
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

  emitSuccess(`Successfully created container "${newFileContainer.name}"`);

  return newFileContainer;
}
