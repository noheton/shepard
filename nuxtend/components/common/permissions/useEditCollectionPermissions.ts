import { CollectionApi } from "@dlr-shepard/backend-client";
import type { UpdatedPermissions } from "~/components/context/collection/edit-dialog/collectionEditTypes";

export function useEditCollectionPermissions(
  collectionId: number,
  onSuccess: () => void,
  isValid: Ref<boolean>,
) {
  const updatedPermissions = ref<UpdatedPermissions>(undefined);

  async function saveChanges() {
    if (isValid.value === false) return;

    if (updatedPermissions.value) {
      const permissionsUpdateSuccess = await createApiInstance(CollectionApi)
        .editCollectionPermissions({
          collectionId: collectionId,
          permissions: updatedPermissions.value,
        })
        .then(_ => {
          return true;
        })
        .catch(error => {
          handleError(error, "updatePermissions");
          return false;
        });
      if (!permissionsUpdateSuccess) return;
    }

    emitSuccess(
      `Successfully updated permissions for collection ID:"${collectionId}"`,
    );
    handleCollectionUpdate();
    onSuccess();
  }

  return {
    updatedPermissions,
    saveChanges,
  };
}
