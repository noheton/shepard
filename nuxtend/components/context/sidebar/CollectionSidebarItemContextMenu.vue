<script setup lang="ts">
import { DataObjectApi } from "@dlr-shepard/backend-client";

const props = defineProps<{
  collectionId: number;
  dataObjectId: number;
  parentId?: number;
  itemName: string;
}>();
const emit = defineEmits([
  "data-object-updated",
  "data-object-created",
  "data-object-deleted",
]);

const showEditDialog = ref(false);
const showCreateDialog = ref(false);
const showDeleteDialog = ref(false);
const showContextMenuButton = ref<boolean>(false);

async function deleteItem() {
  const deletionSuccessful = await createApiInstance(DataObjectApi)
    .deleteDataObject({
      collectionId: props.collectionId,
      dataObjectId: props.dataObjectId,
    })
    .then(() => true)
    .catch(error => {
      handleError(error, "deleteDataObject");
      return false;
    });

  if (!deletionSuccessful) return;

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
      @expansion-state-changed="e => (showContextMenuButton = e)"
    />
    <v-btn
      icon="mdi-plus"
      v-bind="props"
      variant="plain"
      density="compact"
      color="primary"
      @click.prevent.stop="showCreateDialog = true"
    />
  </DisplayChildrenOnHover>
  <CreateDataObjectDialog
    v-if="showCreateDialog"
    v-model:show-dialog="showCreateDialog"
    :collection-id="collectionId"
    :parent-id="dataObjectId"
    @data-object-created="emit('data-object-created')"
  />
  <EditDataObjectDialog
    v-if="showEditDialog"
    v-model:show-dialog="showEditDialog"
    :collection-id="collectionId"
    :data-object-id="dataObjectId"
    :item-name="itemName"
    @data-object-updated="emit('data-object-updated')"
  />

  <ConfirmationDialog
    v-model:show-dialog="showDeleteDialog"
    prompt-text="Are you sure you want to delete this item?"
    confirm-button-text="Delete"
    @confirmed="deleteItem"
  />
</template>
