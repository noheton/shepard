<script lang="ts" setup>
import type { ResponseError } from "@dlr-shepard/backend-client";

const props = defineProps<{
  /**
   * V2-SWEEP Wave 1: collection identifier for v2 calls — the appId (UUID v7)
   * route param (the backend EntityIdResolver also accepts a legacy numeric
   * string).
   */
  collectionId: string;
  collectionAppId?: string;
  /** DataObject appId (UUID v7) — used for the v2 delete + edit. */
  dataObjectId: string;
  /**
   * SIDEBAR-V2-CREATE (aidocs/16): numeric ids resolved from the loaded v2
   * entities, required only by the still-v1-backed create dialog (v1
   * createDataObject + Parent/Predecessor inputs). Never placed on a route.
   */
  collectionNumericId?: number;
  dataObjectNumericId: number;
  itemName: string;
}>();

const emit = defineEmits([
  "data-object-created",
  "data-object-updated",
  "data-object-deleted",
]);

const showEditDialog = ref(false);
const showCreateDialog = ref(false);
const showDeleteDialog = ref(false);
const showContextMenuButton = ref<boolean>(false);

/**
 * BUG-COLL-APPID-ROUTE-005 (2026-06-02): DataObject DELETE routes through
 * `DELETE /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}`.
 * The generated v1 `deleteDataObject({collectionId, dataObjectId})` expects
 * numeric Neo4j longs in the path — post-Neo4j-reset DataObjects carry
 * UUID v7 only, so the v1 path returned 404 and the sidebar delete silently
 * failed (item stayed visible, no toast). The v2 EntityIdResolver accepts
 * either UUID v7 or numeric stringified id transparently.
 */
function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function deleteItem() {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  const url =
    `${v2BaseUrl()}/v2/collections/` +
    `${encodeURIComponent(props.collectionId)}/data-objects/` +
    `${encodeURIComponent(props.dataObjectId)}`;
  const headers: Record<string, string> = { Accept: "application/json" };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;

  const deletionSuccessful = await fetch(url, { method: "DELETE", headers })
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
      handleError(error as ResponseError, "deleteDataObject");
      return false;
    });

  if (!deletionSuccessful) return;

  emitSuccess(`Successfully deleted data object "${props.itemName}"`);
  emit("data-object-deleted");
}
</script>

<template>
  <DisplayChildrenOnHover
    :display-children-without-hover="
      showContextMenuButton ||
      showCreateDialog ||
      showEditDialog ||
      showDeleteDialog
    "
  >
    <div class="d-flex">
      <ContextMenu
        :items="[
          {
            label: 'Edit',
            icon: 'mdi-pencil-outline',
            onClick: () => (showEditDialog = true),
          },
          {
            label: 'Delete',
            icon: 'mdi-delete-outline',
            onClick: () => (showDeleteDialog = true),
          },
        ]"
        @expansion-state-changed="(e: boolean) => (showContextMenuButton = e)"
      />
      <v-btn
        color="primary"
        density="compact"
        icon="mdi-plus"
        v-bind="props"
        variant="plain"
        @click.prevent.stop="showCreateDialog = true"
      />
    </div>
  </DisplayChildrenOnHover>
  <!-- SIDEBAR-V2-CREATE (aidocs/16): the create dialog's blank form is still
       v1-backed — it gets the numeric ids resolved from loaded v2 entities. -->
  <CreateDataObjectDialog
    v-if="showCreateDialog"
    v-model:show-dialog="showCreateDialog"
    :collection-id="(collectionNumericId as unknown as number)"
    :collection-app-id="collectionAppId"
    :parent-id="dataObjectNumericId"
    @data-object-created="emit('data-object-created')"
  />
  <EditDataObjectDialog
    v-if="showEditDialog"
    v-model:show-dialog="showEditDialog"
    :collection-id="collectionId"
    :collection-numeric-id="collectionNumericId"
    :data-object-id="dataObjectId"
    :data-object-name="itemName"
    @data-object-updated="emit('data-object-updated')"
  />
  <ConfirmDeleteDialog
    v-model:show-dialog="showDeleteDialog"
    @confirmed="deleteItem"
  />
</template>
