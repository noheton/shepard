<script lang="ts" setup>
import type { Collection, ResponseError } from "@dlr-shepard/backend-client";

interface CollectionDeleteDialogProps {
  collection: Collection;
}

const props = defineProps<CollectionDeleteDialogProps>();

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const router = useRouter();

/**
 * BUG-COLL-APPID-ROUTE-005 (2026-06-02): Collection DELETE routes through
 * `DELETE /v2/collections/{collectionAppId}`. The generated v1
 * `deleteCollection({collectionId})` expects a numeric Neo4j long; post-
 * Neo4j-reset Collections carry UUID v7 only, so the dialog action
 * silently failed (Collection stayed in the list, no toast). Prefers the
 * `appId` when present and falls back to the numeric `id` for legacy
 * Collections — the v2 EntityIdResolver accepts both.
 */
function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function deleteCollection() {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  // Prefer appId when carried on the Collection IO; fall back to numeric id
  // for legacy callers. The v2 EntityIdResolver accepts either shape.
  const handle =
    (props.collection as unknown as { appId?: string | null }).appId ??
    String(props.collection.id);
  const url = `${v2BaseUrl()}/v2/collections/${encodeURIComponent(handle)}`;
  const headers: Record<string, string> = { Accept: "application/json" };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;

  try {
    const resp = await fetch(url, { method: "DELETE", headers });
    if (!resp.ok) {
      throw {
        response: resp,
        message: `HTTP ${resp.status}`,
      } as unknown as ResponseError;
    }

    showDialog.value = false;
    emitSuccess(`Successfully deleted collection "${props.collection.name}"`);
    await router.push(collectionsPath);
  } catch (error) {
    handleError(error as ResponseError, "deleteCollection");
  }
}
</script>

<template>
  <ConfirmSafeDeleteDialog
    v-model:show-dialog="showDialog"
    :target-name="props.collection.name"
    entity-type="collection"
    @confirmed="deleteCollection"
  />
</template>
