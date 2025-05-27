import { collectionsPath } from "#imports";
import {
  CollectionApi,
  type Collection,
  type Permissions,
  type ResponseError,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";
import { ShepardObjectAccessor } from "../shepardObjectAccessor";

export class CollectionAccessor extends ShepardObjectAccessor {
  api = useShepardApi(CollectionApi);
  collection = ref<Collection>();

  async delete() {
    try {
      if (!this.collection.value) await this.fetchData();
      await this.api.value.deleteCollection({ collectionId: this.id });
      emitSuccess(
        `Successfully deleted collection "${this.collection.value!.name}"`,
      );
      await useRouter().push(collectionsPath);
    } catch (error) {
      handleError(error as ResponseError, "deleting collection");
    }
  }

  async fetchData() {
    try {
      this.collection.value = await this.api.value.getCollection({
        collectionId: this.id,
      });
    } catch (error) {
      handleError(error as ResponseError, "fetching collection");
    }
  }

  async fetchPermissions() {
    try {
      this.permissions.value = await this.api.value.getCollectionPermissions({
        collectionId: this.id,
      });
    } catch (error) {
      handleError(error as ResponseError, "fetching permissions");
    }
  }

  async fetchRoles() {
    try {
      this.roles.value = await this.api.value.getCollectionRoles({
        collectionId: this.id,
      });
    } catch (error) {
      handleError(error as ResponseError, "fetching roles");
    }
  }

  async updatePermissions(updatedPermissions: Permissions) {
    try {
      await this.api.value.editCollectionPermissions({
        collectionId: this.id,
        permissions: updatedPermissions,
      });
      emitSuccess(
        `Successfully updated permissions for collection ID: ${this.id}`,
      );
      handleCollectionUpdate();
    } catch (error) {
      handleError(error as ResponseError, "updating permissions");
    }
  }

  async updateCollection(updatedCollection: Collection) {
    try {
      await this.api.value.updateCollection({
        collectionId: this.id,
        collection: {
          ...updatedCollection,
        },
      });
      emitSuccess(`Successfully updated collection with ID: ${this.id}`);
      handleCollectionUpdate();
    } catch (error) {
      handleError(error as ResponseError, "updating collection");
    }
  }
}
