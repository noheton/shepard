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
    v-model:show-dialog="showDialog"
    @collection-created="id => emit('collection-created', id)"
  >
    <template
      #inputs="{
        updateCollection,
        collection,
        updatePermissionType,
        permissionType,
      }"
    >
      <!-- TODO: Add stepper -->
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
      <v-row>
        <div class="text-body-3 text-textbody2">*mandatory fields</div>
      </v-row>
      <v-row class="pt-8">
        <div class="text-subtitle-2">Attributes</div>
      </v-row>
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
