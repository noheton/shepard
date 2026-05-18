import {
  StructuredDataContainerApi,
  type Permissions,
  type ResponseError,
  type StructuredData,
  type StructuredDataContainer,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";
import { ContainerAccessor } from "../shepardObjectAccessor";
import { safeDeleteContainer } from "./safeDeleteContainer";

export class StructuredDataContainerAccessor extends ContainerAccessor {
  api = useShepardApi(StructuredDataContainerApi);
  container = ref<StructuredDataContainer>();
  items = ref<StructuredData[]>([]);
  loading = ref<boolean>(true);

  override async delete(): Promise<void> {
    // DI1 — see TimeseriesContainerAccessor.delete for rationale.
    try {
      const result = await safeDeleteContainer("structured-data", this.id, {
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
        structuredDataContainerId: this.id,
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
    try {
      this.container.value = await this.api.value.getStructuredDataContainer({
        structuredDataContainerId: this.id,
      });
    } catch (e) {
      handleError(e as ResponseError, "fetching structured data container");
    }
  }

  async fetchItems() {
    this.loading.value = true;
    try {
      this.items.value = await this.api.value.getAllStructuredDatas({
        structuredDataContainerId: this.id,
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
    try {
      this.roles.value = await this.api.value.getStructuredDataRoles({
        structuredDataContainerId: this.id,
      });
    } catch (e) {
      handleError(e as ResponseError, "fetching roles");
    }
  }

  override async fetchPermissions(): Promise<void> {
    try {
      this.permissions.value =
        await this.api.value.getStructuredDataPermissions({
          structuredDataContainerId: this.id,
        });
    } catch (e) {
      handleError(e as ResponseError, "fetching permissions");
      throw e;
    }
  }

  override async updatePermissions(
    updatedPermissions: Permissions,
  ): Promise<void> {
    try {
      await this.api.value.editStructuredDataPermissions({
        structuredDataContainerId: this.id,
        permissions: updatedPermissions,
      });
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
      structuredDataContainerId: this.id,
      structuredDataPayload: {
        structuredData: { name: name },
        payload: content,
      },
    });
  }
}
