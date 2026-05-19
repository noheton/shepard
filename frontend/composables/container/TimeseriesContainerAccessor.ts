import { handleContainerUpdate } from "#imports";
import {
  TimeseriesContainerApi,
  type Permissions,
  type ResponseError,
  type TimeseriesContainer,
  type TimeseriesEntity,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";
import { ContainerAccessor } from "../shepardObjectAccessor";
import { safeDeleteContainer } from "./safeDeleteContainer";

export class TimeseriesContainerAccessor extends ContainerAccessor {
  api = useShepardApi(TimeseriesContainerApi);
  measurements = ref<TimeseriesEntity[]>([]);
  container = ref<TimeseriesContainer>();
  loading = ref<boolean>(true);

  async delete() {
    // DI1: call the /v2/ safe-delete endpoint. The UI has already shown the
    // active-references warning in the confirm dialog, so force=true here.
    // External clients (admin CLI, scripts) that call the same endpoint without
    // force get the server-side 409 protection.
    try {
      const result = await safeDeleteContainer("timeseries", this.id, {
        force: true,
      });
      if (!result.ok) {
        // Shouldn't happen with force=true, but fall back gracefully.
        handleError(
          new Error(
            `Server reported ${result.conflict.referenceCount} active references; delete blocked.`,
          ),
          "deleting timeseries container",
        );
        return;
      }
      emitSuccess(
        `Successfully deleted container "${this.container.value?.name}"`,
      );
      await useRouter().push(containersPath);
    } catch (e) {
      handleError(e as ResponseError, "deleting timeseries container");
      throw e;
    }
  }

  async fetchData() {
    try {
      this.container.value = await this.api.value.getTimeseriesContainer({
        timeseriesContainerId: this.id,
      });
    } catch (e) {
      handleError(e as ResponseError, "fetching timeseries container");
    }
  }

  async fetchMeasurements() {
    try {
      this.loading.value = true;
      this.measurements.value = await this.api.value.getTimeseriesOfContainer({
        timeseriesContainerId: this.id,
      });
    } catch (e) {
      // Was "fetching files" — copy-paste from FileContainerAccessor. The
      // misleading label fired on this TimeseriesContainerAccessor, which
      // is what the user saw on `/containers/timeseries/{id}`.
      handleError(e as ResponseError, "fetching timeseries channels");
      // Don't rethrow — the caller doesn't always swallow it, so a
      // single fetch failure would cascade into an infinite spinner.
      // Clear loading via the finally block and surface the toast.
      this.measurements.value = [];
    } finally {
      this.loading.value = false;
    }
  }

  async fetchPermissions() {
    try {
      this.permissions.value = await this.api.value.getTimeseriesPermissions({
        timeseriesContainerId: this.id,
      });
    } catch (e) {
      handleError(e as ResponseError, "fetching permissions");
      throw e;
    }
  }

  async fetchRoles() {
    try {
      this.roles.value = await this.api.value.getTimeseriesRoles({
        timeseriesContainerId: this.id,
      });
    } catch (e) {
      handleError(e as ResponseError, "fetching roles");
    }
  }

  async updatePermissions(updatedPermissions: Permissions) {
    try {
      await this.api.value.editTimeseriesPermissions({
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

  async uploadMeasurements(file: File) {
    await this.api.value.importTimeseries({
      timeseriesContainerId: this.id,
      file,
    });
  }
}
