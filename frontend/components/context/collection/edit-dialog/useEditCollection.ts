import {
  CollectionApi,
  type Collection,
  type ResponseError,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
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
 * `editCollectionPermissions` stays on v1 — permissions still live on the
 * v1 shelf (BUG-COLL-APPID-ROUTE-PERMS-1 hold-back per
 * `CollectionV2Rest §68-70`). Prefers the `appId` when present, falls back
 * to the numeric `id` for legacy callers (v2 EntityIdResolver accepts both).
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
  // LIC1 + PROMPT-h2: the generated `Collection` model may not yet expose
  // license / accessRights / promptLogMode as top-level fields (the generator
  // runs from an older OpenAPI snapshot). Read defensively from the
  // unknown-shaped object so a fresh backend build with the fields is consumed
  // without regenerating the client.
  const rawCollection = collection as unknown as {
    license?: string | null;
    accessRights?: string | null;
    promptLogMode?: string | null;
  };
  const updatedCollection = ref<UpdatedCollection>({
    name: collection.name,
    attributes: collection.attributes ?? {},
    description: collection.description ?? "",
    status: collection.status ?? null,
    heroImageUrl: collection.heroImageUrl ?? null,
    license: rawCollection.license ?? null,
    accessRights: rawCollection.accessRights ?? null,
    promptLogMode: rawCollection.promptLogMode ?? null,
  });
  const updatedPermissions = ref<UpdatedPermissions>(undefined);

  const collectionApi = useShepardApi(CollectionApi);

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
      const permissionsUpdateSuccess = await collectionApi.value
        .editCollectionPermissions({
          collectionId: collection.id,
          permissions: updatedPermissions.value,
        })
        .then(_ => {
          return true;
        })
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
