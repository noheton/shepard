import {
  type Collection,
  CollectionApi,
  type Permissions,
  type ResponseError,
  type Roles,
  type TimeseriesContainer,
  TimeseriesContainerApi,
  type TimeseriesEntity,
  type User,
  UserApi,
} from "@dlr-shepard/backend-client";
import type { Ref } from "vue";
import { collectionsPath } from "~/utils/constants";
import { handleContainerUpdate } from "~/utils/resourceUpdateBus";

export abstract class ShepardObjectAccessor {
  id: number;
  roles: Ref<Roles | undefined>;
  permissions: Ref<Permissions | undefined>;
  owner: Ref<User | undefined>;

  isAllowedToEditData: ComputedRef<boolean> = computed(() => {
    return !!this.roles.value?.owner || !!this.roles.value?.writer;
  });

  isAllowedToEditPermissions: ComputedRef<boolean> = computed(() => {
    return !!this.roles.value?.owner || !!this.roles.value?.manager;
  });

  constructor(id: number) {
    this.id = id;
    this.roles = ref<Roles>();
    this.permissions = ref();
    this.owner = ref();
  }

  abstract fetchData(): Promise<void>;

  abstract delete(): Promise<void>;

  abstract fetchRoles(): Promise<void>;

  abstract fetchPermissions(): Promise<void>;

  abstract updatePermissions(updatedPermissions: Permissions): Promise<void>;

  async fetchOwner() {
    try {
      if (!this.permissions) {
        await this.fetchPermissions();
      }
      const ownerName = this.permissions.value!.owner!;
      const ownerValue = await createApiInstance(UserApi).getUser({
        username: ownerName,
      });
      this.owner = ref(ownerValue);
    } catch (error) {
      handleError(error as ResponseError, "fetching user");
      throw error;
    }
  }
}

abstract class ContainerAccessor extends ShepardObjectAccessor {
  isAllowedToDelete: ComputedRef<boolean> = computed(() => {
    return !!this.roles.value?.owner;
  });
}

export class TimeseriesContainerAccessor extends ContainerAccessor {
  api = createApiInstance(TimeseriesContainerApi);
  measurements = ref<TimeseriesEntity[]>([]);
  timeseries = ref<TimeseriesContainer>();

  async delete() {
    try {
      await this.api.deleteTimeseriesContainer({
        timeseriesContainerId: this.id,
      });
      emitSuccess(
        `Successfully deleted container "${this.timeseries.value?.name}"`,
      );
      await useRouter().push(containersPath);
    } catch (e) {
      handleError(e as ResponseError, "deleting timeseries container");
      throw e;
    }
  }

  async fetchRoles() {
    try {
      this.roles.value = await this.api.getTimeseriesRoles({
        timeseriesContainerId: this.id,
      });
    } catch (e) {
      handleError(e as ResponseError, "fetching roles");
      throw e;
    }
  }

  async fetchData() {
    try {
      this.timeseries.value = await this.api.getTimeseriesContainer({
        timeseriesContainerId: this.id,
      });
    } catch (e) {
      handleError(e as ResponseError, "fetching timeseries container");
      throw e;
    }
  }

  async fetchMeasurementsTable() {
    try {
      this.measurements.value = await this.api.getTimeseriesOfContainer({
        timeseriesContainerId: this.id,
      });
    } catch (e) {
      handleError(e as ResponseError, "fetching files");
      throw e;
    }
  }

  async fetchPermissions() {
    try {
      this.permissions.value = await this.api.getTimeseriesPermissions({
        timeseriesContainerId: this.id,
      });
    } catch (e) {
      handleError(e as ResponseError, "fetching permissions");
      throw e;
    }
  }

  async updatePermissions(updatedPermissions: Permissions) {
    try {
      await this.api.editTimeseriesPermissions({
        timeseriesContainerId: this.id,
        permissions: updatedPermissions,
      });
      emitSuccess(
        `Successfully updated permissions for timeseries container ID: ${this.id}`,
      );
      handleContainerUpdate();
    } catch (e) {
      handleError(e as ResponseError, "updating permissions");
      throw e;
    }
  }
}

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
