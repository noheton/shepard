import {
  CollectionPermissionsApi,
  type Collection,
  type ResponseError,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import type {
  UpdatedCollection,
  UpdatedPermissions,
} from "./collectionEditTypes";

/**
 * BUG-COLL-APPID-ROUTE-005 (2026-06-02): Collection PATCH routes through
 * `PATCH /v2/collections/{collectionAppId}` (RFC 7396 JSON Merge Patch).
 * The generated v1 `updateCollection({collectionId})` expected a numeric
 * Neo4j long; post-Neo4j-reset Collections carry UUID v7 only so the edit
 * dialog "Save" silently 404'd.
 *
 * `editCollectionPermissions` now uses the v2 endpoint
 * `PUT /v2/collections/{appId}/permissions` (BUG-COLL-APPID-ROUTE-PERMS-1).
 * Falls back to `String(collection.id)` when `appId` is absent (legacy
 * Collections; v2 EntityIdResolver accepts numeric strings too).
 */
function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export function useEditCollection(
  collection: Collection,
  onSuccess: () => void,
  isValid: Ref<boolean>,
  isAllowedToEditPermissions?: boolean,
) {
  // LIC1 + PROMPT-h2: V2-SWEEP-001-CLIENT-REGEN — the regenerated `Collection`
  // model now exposes license / accessRights / promptLogMode as typed
  // top-level fields, so the previous defensive `unknown`-shaped read is gone.
  const updatedCollection = ref<UpdatedCollection>({
    name: collection.name,
    attributes: collection.attributes ?? {},
    description: collection.description ?? "",
    status: collection.status ?? null,
    heroImageUrl: collection.heroImageUrl ?? null,
    license: collection.license ?? null,
    accessRights: collection.accessRights ?? null,
    promptLogMode: collection.promptLogMode ?? null,
  });
  const updatedPermissions = ref<UpdatedPermissions>(undefined);

  async function saveChanges() {
    if (isValid.value === false) return;

    // BUG-COLL-APPID-ROUTE-005: PATCH via v2. Permissions edit on the next
    // step stays on v1 (PERMS-1 hold-back).
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    const handle =
      (collection as unknown as { appId?: string | null }).appId ??
      String(collection.id);
    const v2Url = `${v2BaseUrl()}/v2/collections/${encodeURIComponent(handle)}`;
    const headers: Record<string, string> = {
      "Content-Type": "application/merge-patch+json",
      Accept: "application/json",
    };
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`;

    const collectionUpdateSuccess = await fetch(v2Url, {
      method: "PATCH",
      headers,
      body: JSON.stringify(updatedCollection.value),
    })
      .then(resp => {
        if (!resp.ok) {
          throw {
            response: resp,
            message: `HTTP ${resp.status}`,
          } as unknown as ResponseError;
        }
        return true;
      })
      .catch(error => {
        handleError(error as ResponseError, "updateCollection");
        return false;
      });
    if (!collectionUpdateSuccess) return;

    if (isAllowedToEditPermissions && updatedPermissions.value) {
      const appId = collection.appId ?? String(collection.id);
      const permissionsUpdateSuccess = await useV2ShepardApi(CollectionPermissionsApi)
        .value.editCollectionPermissions({
          appId,
          permissions: updatedPermissions.value,
        })
        .then(_ => true)
        .catch(error => {
          handleError(error, "updatePermissions");
          return false;
        });
      if (!permissionsUpdateSuccess) return;
    }

    emitSuccess(
      `Successfully updated collection "${updatedCollection.value.name}"`,
    );
    handleCollectionUpdate();
    onSuccess();
  }

  return {
    updatedCollection,
    updatedPermissions,
    saveChanges,
  };
}
