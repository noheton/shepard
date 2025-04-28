import { collectionsPath } from "#imports";
import {
  CollectionApi,
  type Collection,
  type Permissions,
  type ResponseError,
} from "@dlr-shepard/backend-client";
import { ShepardObjectAccessor } from "../shepardObjectAccessor";

export class CollectionAccessor extends ShepardObjectAccessor {
  api = createApiInstance(CollectionApi);
  collection = ref<Collection>();

  async delete() {
    try {
      if (!this.collection.value) await this.fetchData();
      await this.api.deleteCollection({ collectionId: this.id });
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
      this.collection.value = await this.api.getCollection({
        collectionId: this.id,
      });
    } catch (error) {
      handleError(error as ResponseError, "fetching collection");
    }
  }

  async fetchPermissions() {
    try {
      this.permissions.value = await this.api.getCollectionPermissions({
        collectionId: this.id,
      });
    } catch (error) {
      handleError(error as ResponseError, "fetching permissions");
    }
  }

  async fetchRoles() {
    try {
      this.roles.value = await this.api.getCollectionRoles({
        collectionId: this.id,
      });
    } catch (error) {
      handleError(error as ResponseError, "fetching roles");
    }
  }

  async updatePermissions(updatedPermissions: Permissions) {
    try {
      await this.api.editCollectionPermissions({
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
}
