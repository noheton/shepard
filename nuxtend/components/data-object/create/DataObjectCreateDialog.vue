<script setup lang="ts">
interface DataObjectEditDialogProps {
  collectionId: number;
  parentId?: number;
}
defineProps<DataObjectEditDialogProps>();
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const emit = defineEmits(["data-object-created"]);
</script>

<template>
  <DataObjectCreateDialogWrapper
    v-model:show-dialog="showDialog"
    :collection-id="collectionId"
    :parent-id="parentId"
    title="Create Data Object"
    @data-object-created="emit('data-object-created')"
  >
    <template
      #inputs="{
        collectionId: editedDataObjectCollectionId,
        updateDataObject,
        updatedDataObject,
      }"
    >
      <!-- TODO: Display as stepper -->
      <v-row>
        <div class="text-subtitle-2">Properties</div>
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
      <v-row class="pt-8">
        <div class="text-subtitle-2">Attributes</div>
      </v-row>
      <CommonInputAttributes
        :attributes="updatedDataObject.attributes ?? {}"
        @attributes-changed="
          attributes =>
            updateDataObject({
              ...updatedDataObject,
              attributes,
            })
        "
      />
    </template>
  </DataObjectCreateDialogWrapper>
</template>
