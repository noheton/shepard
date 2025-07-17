import {
  SpatialDataContainerApi,
  type Permissions,
  type ResponseError,
  type SpatialDataContainer,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";
import { ContainerAccessor } from "../shepardObjectAccessor";

export class SpatialDataContainerAccessor extends ContainerAccessor {
  api = useShepardApi(SpatialDataContainerApi);
  spatialData = ref<SpatialDataContainer>();

  async delete() {
    try {
      await this.api.value.deleteSpatialDataContainer({
        spatialDataContainerId: this.id,
      });
      emitSuccess(
        `Successfully deleted container "${this.spatialData.value?.name}"`,
      );
      await useRouter().push(containersPath);
    } catch (e) {
      handleError(e as ResponseError, "deleting spatial data container");
      throw e;
    }
  }

  async fetchRoles() {
    try {
      this.roles.value = await this.api.value.getSpatialDataRoles({
        spatialDataContainerId: this.id,
      });
    } catch (e) {
      handleError(e as ResponseError, "fetching roles");
      throw e;
    }
  }

  async fetchData() {
    try {
      this.spatialData.value = await this.api.value.getSpatialDataContainer({
        spatialDataContainerId: this.id,
      });
    } catch (e) {
      handleError(e as ResponseError, "fetching spatial data container");
      throw e;
    }
  }

  async fetchPermissions() {
    try {
      this.permissions.value = await this.api.value.getSpatialDataPermissions({
        spatialDataContainerId: this.id,
      });
    } catch (e) {
      handleError(e as ResponseError, "fetching permissions");
      throw e;
    }
  }

  async updatePermissions(updatedPermissions: Permissions) {
    try {
      await this.api.value.editSpatialDataPermissions({
        spatialDataContainerId: this.id,
        permissions: updatedPermissions,
      });
      emitSuccess(
        `Successfully updated permissions for spatial data container ID: ${this.id}`,
      );
      handleContainerUpdate();
    } catch (e) {
      handleError(e as ResponseError, "updating permissions");
      throw e;
    }
  }
}
