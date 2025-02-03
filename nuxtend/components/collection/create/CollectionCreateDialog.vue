<script setup lang="ts">
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const emit = defineEmits<{
  (e: "collection-created", value: number): void;
}>();
</script>

<template>
  <CollectionCreateDialogWrapper
    v-if="showDialog"
    v-model:show-dialog="showDialog"
    @collection-created="id => emit('collection-created', id)"
  >
    <template
      #step1-inputs="{
        updateCollection,
        collection,
        updatePermissionType,
        permissionType,
      }"
    >
      <v-row class="pt-4" />
      <CommonInputName
        :name="collection.name"
        @name-changed="name => updateCollection({ ...collection, name })"
      />
      <CommonInputDescription
        :description="collection.description"
        @description-changed="
          description => updateCollection({ ...collection, description })
        "
      />
      <CommonInputPermissionType
        :permission-type="permissionType"
        :update-permission-type="updatePermissionType"
      />
      <CommonInputMandatoryFieldHint />
    </template>
    <template #step2-inputs="{ updateCollection, collection }">
      <CommonInputAttributes
        :attributes="collection.attributes"
        @attributes-changed="
          attributes =>
            updateCollection({
              ...collection,
              attributes,
            })
        "
      />
    </template>
  </CollectionCreateDialogWrapper>
</template>
