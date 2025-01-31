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
  <CollectionSidebarDisplayChildrenOnHover
    :display-children-without-hover="
      showContextMenuButton ||
      showCreateDialog ||
      showEditDialog ||
      showDeleteDialog
    "
  >
    <CommonContextMenu
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
  </CollectionSidebarDisplayChildrenOnHover>
  <DataObjectCreateDialog
    v-model:show-dialog="showCreateDialog"
    :collection-id="collectionId"
    :parent-id="dataObjectId"
    @data-object-created="emit('data-object-created')"
  />
  <DataObjectEditDialog
    v-if="showEditDialog"
    v-model:show-dialog="showEditDialog"
    :collection-id="collectionId"
    :data-object-id="dataObjectId"
    :parent-id="parentId"
    :title="`Edit &quot;${itemName}&quot;`"
    @data-object-updated="emit('data-object-updated')"
  >
    <template
      #inputs="{
        collectionId: editedDataObjectCollectionId,
        updateDataObject,
        updatedDataObject,
      }"
    >
      <v-row class="pt-8" />
      <v-row>
        <v-col class="pb-2">
          <div class="text-subtitle-1">Properties</div>
        </v-col>
      </v-row>
      <CommonInputName
        :name="updatedDataObject.name"
        @name-changed="name => updateDataObject({ ...updatedDataObject, name })"
      />
      <CommonInputDescription
        :description="updatedDataObject.description"
        @description-changed="
          description => updateDataObject({ ...updatedDataObject, description })
        "
      />
      <CommonInputMandatoryFieldHint />
      <v-row>
        <v-col class="pt-8 pb-2">
          <div class="text-subtitle-1">Relationships</div>
        </v-col>
      </v-row>
      <DataObjectEditParentInput
        :collection-id="editedDataObjectCollectionId"
        :parent-id="updatedDataObject.parentId"
        @parent-changed="
          parentId => updateDataObject({ ...updatedDataObject, parentId })
        "
      />
      <DataObjectEditPredecessorInput
        :collection-id="editedDataObjectCollectionId"
        :predecessor-ids="updatedDataObject.predecessorIds ?? []"
        @predecessors-changed="
          predecessorIds =>
            updateDataObject({ ...updatedDataObject, predecessorIds })
        "
      />
    </template>
  </DataObjectEditDialog>
  <CommonConfirmationDialog
    v-model:show-dialog="showDeleteDialog"
    prompt-text="Are you sure you want to delete this item?"
    confirm-button-text="Delete"
    @confirmed="deleteItem"
  />
</template>
