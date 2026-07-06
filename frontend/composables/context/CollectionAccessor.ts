import { collectionsPath } from "#imports";
import {
  CollectionPermissionsApi,
  type Collection,
  type Permissions,
  type ResponseError,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";
import { ShepardObjectAccessor } from "../shepardObjectAccessor";

/**
 * BUG-COLL-APPID-ROUTE-003 (2026-06-02): GET / DELETE / PATCH on a Collection
 * route through the v2 appId-keyed endpoints
 * (`/v2/collections/{collectionAppId}`) so post-Neo4j-reset Collections
 * (UUID v7 only, no numeric long `id`) resolve. The v2 backing resolver
 * (`EntityIdResolver`) accepts UUID-v7 or numeric stringified id, so legacy
 * numeric handles keep working.
 *
 * BUG-COLL-APPID-ROUTE-PERMS-1 (2026-07-06): Permissions / roles now use the
 * v2 endpoints `GET/PUT /v2/collections/{appId}/permissions` and
 * `GET /v2/collections/{appId}/roles`. The `appId` is taken from the loaded
 * Collection entity; if absent (should not happen post-L2d), the call
 * fails-soft as before.
 */

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function v2Fetch(
  path: string,
  init: RequestInit,
): Promise<Response> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  const headers: Record<string, string> = {
    ...((init.headers as Record<string, string> | undefined) ?? {}),
    Accept: "application/json",
  };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  const resp = await fetch(`${v2BaseUrl()}${path}`, { ...init, headers });
  if (!resp.ok) {
    throw {
      response: resp,
      message: `HTTP ${resp.status}`,
    } as unknown as ResponseError;
  }
  return resp;
}

export class CollectionAccessor extends ShepardObjectAccessor<string> {
  collection = ref<Collection>();

  /**
   * Stringified handle used in v2 paths. Accepts either the legacy numeric
   * id (passed through `super(id)` as a number) or — when callers eventually
   * widen — a UUID v7. `encodeURIComponent` over `String(id)` keeps both
   * shapes safe to embed in the URL.
   */
  private get pathId(): string {
    return encodeURIComponent(String(this.id));
  }

  async delete() {
    try {
      if (!this.collection.value) await this.fetchData();
      await v2Fetch(`/v2/collections/${this.pathId}`, { method: "DELETE" });
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
      const resp = await v2Fetch(`/v2/collections/${this.pathId}`, {
        method: "GET",
      });
      this.collection.value = (await resp.json()) as Collection;
    } catch (error) {
      handleError(error as ResponseError, "fetching collection");
    }
  }

  async fetchPermissions() {
    try {
      if (!this.collection.value) await this.fetchData();
      const appId = this.collection.value?.appId;
      if (!appId) return;
      this.permissions.value = await useV2ShepardApi(CollectionPermissionsApi)
        .value.getCollectionPermissions({ appId });
    } catch (error) {
      handleError(error as ResponseError, "fetching permissions");
      throw error;
    }
  }

  async fetchRoles() {
    try {
      if (!this.collection.value) await this.fetchData();
      const appId = this.collection.value?.appId;
      if (!appId) return;
      this.roles.value = await useV2ShepardApi(CollectionPermissionsApi)
        .value.getCollectionRoles({ appId });
    } catch (error) {
      handleError(error as ResponseError, "fetching roles");
    }
  }

  async updatePermissions(updatedPermissions: Permissions) {
    try {
      if (!this.collection.value) await this.fetchData();
      const appId = this.collection.value?.appId;
      if (!appId) {
        handleError(
          new Error("Cannot update permissions: Collection has no appId."),
          "updating permissions",
        );
        return;
      }
      await useV2ShepardApi(CollectionPermissionsApi).value.editCollectionPermissions({
        appId,
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
      await v2Fetch(`/v2/collections/${this.pathId}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/merge-patch+json" },
        body: JSON.stringify({ ...updatedCollection }),
      });
      emitSuccess(`Successfully updated collection with ID: ${this.id}`);
      handleCollectionUpdate();
    } catch (error) {
      handleError(error as ResponseError, "updating collection");
    }
  }
}
