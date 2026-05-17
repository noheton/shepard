<script lang="ts" setup>
import { DataObjectApi } from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

const props = defineProps<{
  collectionId: number;
  collectionAppId?: string;
  dataObjectId: number;
  parentId?: number;
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

async function deleteItem() {
  const deletionSuccessful = await useShepardApi(DataObjectApi)
    .value.deleteDataObject({
      collectionId: props.collectionId,
      dataObjectId: props.dataObjectId,
    })
    .then(() => true)
    .catch(error => {
      handleError(error, "deleteDataObject");
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
  <CreateDataObjectDialog
    v-if="showCreateDialog"
    v-model:show-dialog="showCreateDialog"
    :collection-id="collectionId"
    :collection-app-id="collectionAppId"
    :parent-id="dataObjectId"
    @data-object-created="emit('data-object-created')"
  />
  <EditDataObjectDialog
    v-if="showEditDialog"
    v-model:show-dialog="showEditDialog"
    :collection-id="collectionId"
    :data-object-id="dataObjectId"
    :data-object-name="itemName"
    @data-object-updated="emit('data-object-updated')"
  />
  <ConfirmDeleteDialog
    v-model:show-dialog="showDeleteDialog"
    @confirmed="deleteItem"
  />
</template>
