import { CollectionApi, type Collection } from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
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
  // LIC1: the generated `Collection` model may not yet expose license /
  // accessRights as top-level fields (the generator runs from an older OpenAPI
  // snapshot). Read defensively from the unknown-shaped object so a fresh
  // backend build with the fields is consumed without regenerating the client.
  const rawCollection = collection as unknown as {
    license?: string | null;
    accessRights?: string | null;
  };
  const updatedCollection = ref<UpdatedCollection>({
    name: collection.name,
    attributes: collection.attributes ?? {},
    description: collection.description ?? "",
    status: collection.status ?? null,
    heroImageUrl: collection.heroImageUrl ?? null,
    license: rawCollection.license ?? null,
    accessRights: rawCollection.accessRights ?? null,
  });
  const updatedPermissions = ref<UpdatedPermissions>(undefined);

  const collectionApi = useShepardApi(CollectionApi);

  async function saveChanges() {
    if (isValid.value === false) return;

    const collectionUpdateSuccess = await collectionApi.value
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
      const permissionsUpdateSuccess = await collectionApi.value
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
