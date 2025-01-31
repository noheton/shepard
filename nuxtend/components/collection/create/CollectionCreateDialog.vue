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
      <v-row class="pt-8" />
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
      <v-row class="pt-8">
        <v-col>
          <div class="text-subtitle-2">Attributes</div>
        </v-col>
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
