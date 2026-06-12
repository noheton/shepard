import {
  StructuredDataContainerApi,
  type Permissions,
  type ResponseError,
  type Roles,
  type StructuredData,
  type StructuredDataContainer,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";
import { ContainerAccessor } from "../shepardObjectAccessor";
import { safeDeleteContainer } from "./safeDeleteContainer";
import { v2BaseUrl } from "./createV2Container";

export class StructuredDataContainerAccessor extends ContainerAccessor {
  api = useShepardApi(StructuredDataContainerApi);
  container = ref<StructuredDataContainer>();
  items = ref<StructuredData[]>([]);
  loading = ref<boolean>(true);

  // Resolve numeric Neo4j id for v1 API calls that still require it.
  private get numericId(): number {
    if (/^\d+$/.test(this.id)) return Number(this.id);
    return this.container.value?.id ?? 0;
  }

  override async delete(): Promise<void> {
    // DI1 — see TimeseriesContainerAccessor.delete for rationale.
    try {
      const containerAppId = this.container.value?.appId ?? this.id;
      const result = await safeDeleteContainer("structured-data", containerAppId, {
        force: true,
      });
      if (!result.ok) {
        handleError(
          new Error(
            `Server reported ${result.conflict.referenceCount} active references; delete blocked.`,
          ),
          "deleting structured data container",
        );
        return;
      }
      emitSuccess(
        `Successfully deleted container "${this.container.value?.name}"`,
      );
      await useRouter().push(containersPath);
    } catch (e) {
      handleError(e as ResponseError, "deleting structured data container");
      throw e;
    }
  }

  async deleteItem(oid: string): Promise<void> {
    try {
      await this.api.value.deleteStructuredData({
        structuredDataContainerId: this.numericId,
        oid,
      });
      emitSuccess(`Successfully deleted item "${oid}"`);
      handleContainerUpdate();
    } catch (e) {
      handleError(e as ResponseError, "deleting structured data item");
      throw e;
    }
  }

  override async fetchData(): Promise<void> {
    const idStr = this.id;
    if (/^\d+$/.test(idStr)) {
      // V1-EXCEPTION: HeaderBar search still routes numeric ids (SEARCH-V2 will retire).
      try {
        this.container.value = await this.api.value.getStructuredDataContainer({
          structuredDataContainerId: Number(idStr),
        });
      } catch (e) {
        handleError(e as ResponseError, "fetching structured data container");
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
        handleError(new Error(`HTTP ${resp.status}`), "fetching structured data container");
        return;
      }
      this.container.value = await resp.json() as StructuredDataContainer;
    } catch (e) {
      handleError(e as ResponseError, "fetching structured data container");
    }
  }

  async fetchItems() {
    this.loading.value = true;
    try {
      this.items.value = await this.api.value.getAllStructuredDatas({
        structuredDataContainerId: this.numericId,
      });
    } catch (e) {
      handleError(
        e as ResponseError,
        "fetching structured data container items",
      );
    } finally {
      this.loading.value = false;
    }
  }

  override async fetchRoles(): Promise<void> {
    // V2-SWEEP-003-1: v2 unified roles (replaces v1 getStructuredDataRoles)
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

  override async fetchPermissions(): Promise<void> {
    // v1-generated model omits appId though the wire carries it (same cast as useFetchCollection)
    const containerAppId = (this.container.value as unknown as { appId?: string | null } | undefined)?.appId;
    if (!containerAppId) throw new Error("Container appId not available — call fetchData() first");
    try {
      // V2-SWEEP-003-2: v2 unified permissions (replaces v1 getStructuredDataPermissions)
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

  override async updatePermissions(
    updatedPermissions: Permissions,
  ): Promise<void> {
    // v1-generated model omits appId though the wire carries it (same cast as useFetchCollection)
    const containerAppId = (this.container.value as unknown as { appId?: string | null } | undefined)?.appId;
    if (!containerAppId) throw new Error("Container appId not available — call fetchData() first");
    try {
      // V2-SWEEP-003-2: v2 unified permissions (replaces v1 editStructuredDataPermissions)
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
        `Successfully updated permissions for structured data container: ${this.id}`,
      );
      handleContainerUpdate();
    } catch (e) {
      handleError(e as ResponseError, "updating permissions");
      throw e;
    }
  }

  async uploadItem(name: string, content: string): Promise<void> {
    await this.api.value.createStructuredData({
      structuredDataContainerId: this.numericId,
      structuredDataPayload: {
        structuredData: { name: name },
        payload: content,
      },
    });
  }
}
