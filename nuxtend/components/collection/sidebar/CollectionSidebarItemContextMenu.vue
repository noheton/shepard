<script setup lang="ts">
const props = defineProps<{
  collectionId: number;
  dataObjectId: number;
  parentId?: number;
  itemName: string;
  deleteItem: () => Promise<void>;
}>();
const emit = defineEmits(["data-object-updated"]);

const showEditDialog = ref(false);
const showDeleteDialog = ref(false);

const dialogTitle = `Edit "${props.itemName}"`;
</script>

<template>
  <div>
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
    />
    <DataObjectEditDialog
      v-if="showEditDialog"
      v-model:show-dialog="showEditDialog"
      :collection-id="collectionId"
      :data-object-id="dataObjectId"
      :parent-id="parentId"
      :title="dialogTitle"
      @data-object-updated="emit('data-object-updated')"
    >
      <template
        #inputs="{
          collectionId: editedDataObjectCollectionId,
          updateDataObject,
          updatedDataObject,
        }"
      >
        <v-row>
          <div class="text-subtitle-2">Properties</div>
        </v-row>
        <CommonInputName
          :name="updatedDataObject.name"
          @name-changed="
            name => updateDataObject({ ...updatedDataObject, name })
          "
        />
        <CommonInputDescription
          :description="updatedDataObject.description"
          @description-changed="
            description =>
              updateDataObject({ ...updatedDataObject, description })
          "
        />
        <v-row>
          <div class="text-body-3 text-textbody2">*mandatory fields</div>
        </v-row>
        <v-row class="pt-8">
          <div class="text-subtitle-2">Relationships</div>
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
  </div>
</template>
