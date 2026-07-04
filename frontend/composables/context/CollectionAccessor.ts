import { collectionsPath } from "#imports";
import {
  CollectionApi,
  type Collection,
  type Permissions,
  type ResponseError,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";
import { ShepardObjectAccessor } from "../shepardObjectAccessor";

/**
 * BUG-COLL-APPID-ROUTE-003 (2026-06-02): GET / DELETE / PATCH on a Collection
 * route through the v2 appId-keyed endpoints
 * (`/v2/collections/{collectionAppId}`) so post-Neo4j-reset Collections
 * (UUID v7 only, no numeric long `id`) resolve. The v2 backing resolver
 * (`EntityIdResolver`) accepts UUID-v7 or numeric stringified id, so legacy
 * numeric handles keep working.
 *
 * Permissions / roles still live on the v1 shelf — `CollectionV2Rest`
 * explicitly defers them to a future `/v2/permissions/{collectionAppId}`
 * resource. The accessor calls v1 with the numeric `id` read off the v2
 * response, mirroring `useFetchCollection.ts` line 106. If the numeric id is
 * absent (post-reset Collection), the permissions / roles call fails-soft —
 * the page renders without those slices rather than redirecting on error.
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
  api = useShepardApi(CollectionApi);
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
    // Permissions live on the v1 shelf only — see CollectionV2Rest §68-70.
    // We need a numeric `id` to call the v1 endpoint; if the Collection
    // has none (post-reset), make sure we've loaded it via v2 first to
    // pick up `id` from the response. If still null afterwards, skip
    // fail-soft rather than 400 on the v1 call.
    try {
      if (!this.collection.value) await this.fetchData();
      const numericId = this.collection.value?.id;
      if (numericId == null) {
        // Post-reset Collection — no numeric id, no v1 permissions surface.
        // Leave `this.permissions` undefined; the page is responsible for
        // rendering a degraded view.
        return;
      }
      this.permissions.value = await this.api.value.getCollectionPermissions({
        collectionId: numericId,
      });
    } catch (error) {
      handleError(error as ResponseError, "fetching permissions");
      throw error;
    }
  }

  async fetchRoles() {
    // Roles live on the v1 shelf only — see CollectionV2Rest §68-70. Same
    // numeric-id fallback as `fetchPermissions()`.
    try {
      if (!this.collection.value) await this.fetchData();
      const numericId = this.collection.value?.id;
      if (numericId == null) {
        return;
      }
      this.roles.value = await this.api.value.getCollectionRoles({
        collectionId: numericId,
      });
    } catch (error) {
      handleError(error as ResponseError, "fetching roles");
    }
  }

  async updatePermissions(updatedPermissions: Permissions) {
    // editCollectionPermissions is v1-only (per the same hold-back as
    // permissions reads). Numeric id required.
    try {
      if (!this.collection.value) await this.fetchData();
      const numericId = this.collection.value?.id;
      if (numericId == null) {
        handleError(
          new Error(
            "Cannot update permissions: Collection has no numeric id (post-reset).",
          ),
          "updating permissions",
        );
        return;
      }
      await this.api.value.editCollectionPermissions({
        collectionId: numericId,
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
