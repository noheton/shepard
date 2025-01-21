<script setup lang="ts">
import { CollectionApi, type Collection } from "@dlr-shepard/backend-client";

const dialog = ref<boolean>(false);

interface EntityAttributesEditDialogProps {
  entity: Collection;
}

const props = defineProps<EntityAttributesEditDialogProps>();

function mapAttributes() {
  if (!props.entity.attributes) return;
  const keys = Object.keys(props.entity.attributes);
  const attributesMapped = new Map<string, string>();
  keys.forEach(key => {
    if (!props.entity.attributes) return;
    attributesMapped.set(key, props.entity.attributes[key] ?? "");
  });
  return attributesMapped;
}

const mappedAttributes = ref(mapAttributes());

function addAttribute() {
  if (!mappedAttributes.value) return;
  mappedAttributes.value.set("", "");
}

async function saveChanges() {
  //Todo: Proper handling of request
  createApiInstance(CollectionApi)
    .updateCollection({
      collectionId: props.entity.id,
      collection: {
        ...mappedAttributes.value,
        ...props.entity,
      },
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
        <div v-for="(attribute, index) in mappedAttributes" :key="index">
          <v-row :key="index">
            <v-col>
              <!-- Todo: Add v-models to textfield with key-value pair -->
              <v-text-field label="Key" variant="outlined" density="compact" />
            </v-col>
            <v-col>
              <v-text-field
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
                @click="addAttribute"
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
