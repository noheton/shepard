import {
  FileContainerApi,
  type PermissionType,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";
import { createV2Container } from "../container/createV2Container";

/**
 * V2CONV-A3: container creation now goes through the unified
 * `POST /v2/containers?kind=file` surface (hand-written fetch via
 * {@link createV2Container}; the generated `@dlr-shepard/backend-client` has no
 * ContainersV2Api yet — regen needs the OpenAPI toolchain absent in the
 * worktree).
 *
 * The subsequent permission setup still uses the v1 FileContainerApi: there is
 * no `/v2/` container-permission endpoint yet (the `getCollectionRoles`-class
 * v1 fallback). It is keyed by the numeric `id` resolved from the freshly
 * created v2 container — never a route param — per the v2-only-with-named-
 * v1-fallback rule. Backlog: CONTAINER-PERMS-V2 in aidocs/16.
 */
export async function useCreateFileContainer(
  fileContainerName: string,
  permissionType: PermissionType,
) {
  const newFileContainer = await createV2Container("file", fileContainerName);
  if (!newFileContainer) return;

  const api = useShepardApi(FileContainerApi);

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
