import { handleContainerUpdate } from "#imports";
import {
  TimeseriesContainerApi,
  type Permissions,
  type ResponseError,
  type TimeseriesContainer,
  type TimeseriesEntity,
} from "@dlr-shepard/backend-client";
import { ContainerAccessor } from "../shepardObjectAccessor";

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
