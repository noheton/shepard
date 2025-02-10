import { CollectionApi, type Collection } from "@dlr-shepard/backend-client";
import type {
  UpdatedCollection,
  UpdatedPermissions,
} from "./collectionEditTypes";

export function useEditCollection(
  collection: Collection,
  onSuccess: () => void,
  isValid: Ref<boolean>,
  isAllowedToEditPermissions?: boolean,
) {
  const updatedCollection = ref<UpdatedCollection>({
    name: collection.name,
    attributes: collection.attributes ?? {},
    description: collection.description ?? "",
  });
  const updatedPermissions = ref<UpdatedPermissions>(undefined);

  async function saveChanges() {
    if (isValid.value === false) return;

    const collectionUpdateSuccess = await createApiInstance(CollectionApi)
      .updateCollection({
        collectionId: collection.id,
        collection: updatedCollection.value,
      })
      .then(_ => {
        return true;
      })
      .catch(error => {
        handleError(error, "updateCollection");
        return false;
      });
    if (!collectionUpdateSuccess) return;

    if (isAllowedToEditPermissions && updatedPermissions.value) {
      const permissionsUpdateSuccess = await createApiInstance(CollectionApi)
        .editCollectionPermissions({
          collectionId: collection.id,
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
      `Successfully updated collection "${updatedCollection.value.name}"`,
    );
    handleCollectionUpdate();
    onSuccess();
  }

  return {
    updatedCollection,
    updatedPermissions,
    saveChanges,
  };
}
