import { handleContainerUpdate } from "#imports";
import {
  TimeseriesContainerApi,
  type Permissions,
  type ResponseError,
  type Roles,
  type TimeseriesContainer,
  type TimeseriesEntity,
} from "@dlr-shepard/backend-client";
import { MAX_CHANNEL_PAGE_SIZE } from "~/utils/channelConstants";
import { useShepardApi } from "../common/api/useShepardApi";
import { ContainerAccessor } from "../shepardObjectAccessor";
import { safeDeleteContainer } from "./safeDeleteContainer";
import { v2BaseUrl } from "./createV2Container";
import { unwrapList } from "~/utils/unwrapList";

export class TimeseriesContainerAccessor extends ContainerAccessor {
  api = useShepardApi(TimeseriesContainerApi);
  measurements = ref<TimeseriesEntity[]>([]);
  container = ref<TimeseriesContainer>();
  loading = ref<boolean>(true);

  // Resolve numeric Neo4j id for v1 API calls that still require it.
  private get numericId(): number {
    if (/^\d+$/.test(this.id)) return Number(this.id);
    return this.container.value?.id ?? 0;
  }

  async delete() {
    // DI1: call the /v2/ safe-delete endpoint. The UI has already shown the
    // active-references warning in the confirm dialog, so force=true here.
    // External clients (admin CLI, scripts) that call the same endpoint without
    // force get the server-side 409 protection.
    try {
      // V2-SWEEP-003-2: this.id is the appId from the route (UUID) or numeric string (V1-EXCEPTION).
      const containerAppId = this.container.value?.appId ?? this.id;
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
    const idStr = this.id;
    if (/^\d+$/.test(idStr)) {
      // V1-EXCEPTION: HeaderBar search still routes numeric ids (SEARCH-V2 will retire).
      try {
        this.container.value = await this.api.value.getTimeseriesContainer({
          timeseriesContainerId: Number(idStr),
        });
      } catch (e) {
        handleError(e as ResponseError, "fetching timeseries container");
      }
      return;
    }
    // V2-SWEEP-003-2: appId path — used when navigated from CollectionContainersPanel.
    try {
      const { data: session } = useAuth();
      const accessToken = (session.value as unknown as { accessToken?: string } | null)?.accessToken;
      const headers: Record<string, string> = { Accept: "application/json" };
      if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
      const resp = await fetch(
        `${v2BaseUrl()}/v2/containers/${encodeURIComponent(idStr)}`,
        { method: "GET", headers },
      );
      if (!resp.ok) {
        handleError(new Error(`HTTP ${resp.status}`), "fetching timeseries container");
        return;
      }
      this.container.value = await resp.json() as TimeseriesContainer;
    } catch (e) {
      handleError(e as ResponseError, "fetching timeseries container");
    }
  }

  // Cap the flat channel listing. A single shared container can hold hundreds of
  // thousands of channels (MFFD tapelaying = ~574k); the v1 getTimeseriesOfContainer
  // path was both unpaginated AND required a numeric id that the appId-routed page
  // can't resolve (→ "Container with id 0"). Per-track access goes through each
  // DataObject's TimeseriesReference (~190 channels); this flat view is a bounded
  // sample. Full server-side-paginated browse is a follow-up (TS-CONTAINER-CHANNEL-PAGE).
  private static readonly CHANNEL_PAGE_SIZE = MAX_CHANNEL_PAGE_SIZE;

  async fetchMeasurements() {
    try {
      this.loading.value = true;
      // V2-only: appId-keyed channel listing (no numeric id needed).
      const containerAppId =
        (this.container.value as unknown as { appId?: string | null } | undefined)?.appId ?? this.id;
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const headers: Record<string, string> = { Accept: "application/json" };
      if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
      const resp = await fetch(
        `${v2BaseUrl()}/v2/containers/${encodeURIComponent(containerAppId)}/channels`
          + `?pageSize=${TimeseriesContainerAccessor.CHANNEL_PAGE_SIZE}`,
        { method: "GET", headers },
      );
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      // The v2 channel IO is a superset of the v1 TimeseriesEntity 5-tuple.
      this.measurements.value = unwrapList<TimeseriesEntity>(await resp.json());
    } catch (e) {
      handleError(e as ResponseError, "fetching timeseries channels");
      // Don't rethrow — a single fetch failure would otherwise cascade into an
      // infinite spinner. Clear loading via finally and surface the toast.
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
      timeseriesContainerId: this.numericId,
      file,
    });
  }
}
