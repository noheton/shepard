import { handleContainerUpdate } from "#imports";
import {
  TimeseriesContainerApi,
  type Permissions,
  type ResponseError,
  type Roles,
  type TimeseriesContainer,
  type TimeseriesEntity,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";
import { ContainerAccessor } from "../shepardObjectAccessor";
import { safeDeleteContainer } from "./safeDeleteContainer";
import { v2BaseUrl } from "./createV2Container";

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
      // APISIMP-TSCONT-APPID-KEY: DELETE keyed on appId; fall back to numeric
      // this.id only if the container hasn't been loaded yet (graceful degradation).
      const containerAppId = (this.container.value as unknown as { appId?: string | null })?.appId ?? this.id;
      const result = await safeDeleteContainer("timeseries", containerAppId, {
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
    // v1-generated model omits appId though the wire carries it (same cast as useFetchCollection)
    const containerAppId = (this.container.value as unknown as { appId?: string | null } | undefined)?.appId;
    if (!containerAppId) throw new Error("Container appId not available — call fetchData() first");
    try {
      // V2-SWEEP-003-2: v2 unified permissions (replaces v1 getTimeseriesPermissions)
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const headers: Record<string, string> = { Accept: "application/json" };
      if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
      const resp = await fetch(
        `${v2BaseUrl()}/v2/containers/${encodeURIComponent(containerAppId)}/permissions`,
        { method: "GET", headers },
      );
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      this.permissions.value = (await resp.json()) as Permissions;
    } catch (e) {
      handleError(e as ResponseError, "fetching permissions");
      throw e;
    }
  }

  async fetchRoles() {
    // V2-SWEEP-003-1: v2 unified roles (replaces v1 getTimeseriesRoles)
    const containerAppId = (this.container.value as unknown as { appId?: string | null } | undefined)?.appId;
    if (!containerAppId) throw new Error("Container appId not available — call fetchData() first");
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const headers: Record<string, string> = { Accept: "application/json" };
      if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
      const resp = await fetch(
        `${v2BaseUrl()}/v2/containers/${encodeURIComponent(containerAppId)}/roles`,
        { method: "GET", headers },
      );
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      this.roles.value = (await resp.json()) as Roles;
    } catch (e) {
      handleError(e as ResponseError, "fetching roles");
      throw e;
    }
  }

  async updatePermissions(updatedPermissions: Permissions) {
    // v1-generated model omits appId though the wire carries it (same cast as useFetchCollection)
    const containerAppId = (this.container.value as unknown as { appId?: string | null } | undefined)?.appId;
    if (!containerAppId) throw new Error("Container appId not available — call fetchData() first");
    try {
      // V2-SWEEP-003-2: v2 unified permissions (replaces v1 editTimeseriesPermissions)
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const headers: Record<string, string> = {
        "Content-Type": "application/json",
        Accept: "application/json",
      };
      if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
      const resp = await fetch(
        `${v2BaseUrl()}/v2/containers/${encodeURIComponent(containerAppId)}/permissions`,
        { method: "PATCH", headers, body: JSON.stringify(updatedPermissions) },
      );
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
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
