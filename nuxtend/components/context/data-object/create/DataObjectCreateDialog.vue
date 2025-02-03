<script setup lang="ts">
interface DataObjectCreateDialogProps {
  collectionId: number;
  parentId?: number;
}
defineProps<DataObjectCreateDialogProps>();
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
      <v-row class="pt-8" />
      <v-row>
        <v-col>
          <div class="text-subtitle-2">Properties</div>
        </v-col>
      </v-row>
      <NameInput
        :name="updatedDataObject.name"
        @name-changed="name => updateDataObject({ ...updatedDataObject, name })"
      />
      <DescriptionInput
        :description="updatedDataObject.description"
        @description-changed="
          description => updateDataObject({ ...updatedDataObject, description })
        "
      />
      <MandatoryFieldHint />
      <v-row class="pt-8">
        <v-col>
          <div class="text-subtitle-2">Relationships</div>
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
      <v-row class="pt-8">
        <v-col>
          <div class="text-subtitle-2">Attributes</div>
        </v-col>
      </v-row>
      <AttributesInput
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
