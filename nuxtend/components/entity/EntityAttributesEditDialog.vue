<script setup lang="ts">
import {
  CollectionApi,
  type Collection,
  type UpdateCollectionRequest,
} from "@dlr-shepard/backend-client";

const dialog = ref<boolean>(false);

interface EntityAttributesEditDialogProps {
  collection: Collection;
}

const props = defineProps<EntityAttributesEditDialogProps>();

const updatedCollection = ref<
  UpdateCollectionRequest["collection"] & {
    attributes: { [key: string]: string };
  }
>({
  name: props.collection.name,
  attributes: props.collection.attributes ?? {},
});

function deleteAttribute(index: string) {
  const { [index]: removed, ...newAttributes } =
    updatedCollection.value.attributes;
  updatedCollection.value.attributes = newAttributes;
}

async function saveChanges() {
  //Todo: Proper handling of request
  createApiInstance(CollectionApi)
    .updateCollection({
      collectionId: props.collection.id,
      collection: updatedCollection.value,
    })
    .then()
    .catch(error => error);

  dialog.value = false;
}
</script>

<template>
  <v-dialog v-model="dialog" max-width="600">
    <template #activator="{ props: activatorProps }">
      <span v-bind="activatorProps">
        <v-btn
          class="text-body-1"
          text="Add/Edit"
          variant="text"
          color="primary"
          prepend-icon="mdi-plus-circle"
        />
      </span>
    </template>
    <v-card class="pa-0 bg-canvas" title="Add / Edit Attributes">
      <v-card-text>
        <div
          v-for="(attribute, index) in updatedCollection.attributes"
          :key="index"
        >
          <v-row>
            <v-col>
              <!-- Todo: Add v-models to textfield with key-value pair -->
              <v-text-field label="Key" variant="outlined" density="compact" />
            </v-col>
            <v-col>
              <v-text-field
                v-model="updatedCollection.attributes[index]"
                label="Value"
                variant="outlined"
                density="compact"
              />
            </v-col>
            <v-col>
              <v-btn
                class="text-textbody1 text-body-1"
                icon="mdi-delete-outline"
                color="canvas"
                @click="deleteAttribute(index.toString())"
              />
            </v-col>
          </v-row>
        </div>
        <v-btn
          text="Add Attributes"
          color="treeview"
          prepend-icon="mdi-plus-circle"
        />
      </v-card-text>

      <template #actions>
        <v-btn variant="flat" color="treeview" @click="dialog = false">
          Cancel
        </v-btn>

        <v-btn variant="flat" color="primary" @click="saveChanges">
          Save Changes
        </v-btn>
      </template>
    </v-card>
  </v-dialog>
</template>
