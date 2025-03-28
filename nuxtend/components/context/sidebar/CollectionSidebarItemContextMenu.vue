<script setup lang="ts">
import { DataObjectApi } from "@dlr-shepard/backend-client";
import ConfirmDeleteDialog from "~/components/common/ConfirmDeleteDialog.vue";

const props = defineProps<{
  collectionId: number;
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
    </div>
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
    :data-object-name="itemName"
    @data-object-updated="emit('data-object-updated')"
  />
  <ConfirmDeleteDialog
    v-model:show-dialog="showDeleteDialog"
    @confirmed="deleteItem"
  />
</template>
