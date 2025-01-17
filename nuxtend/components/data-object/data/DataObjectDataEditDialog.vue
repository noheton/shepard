<script setup lang="ts">
import { DataObjectApi } from "@dlr-shepard/backend-client";

interface DataObjectEditDialogProps {
  collectionId: number;
  dataObjectId: number;
}

const props = defineProps<DataObjectEditDialogProps>();
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const { dataObject } = useDataObject(props.collectionId, props.dataObjectId);
const isValid = ref(false);
const nameRules = [
  (value: unknown) => {
    if (value) return true;
    return "Name is required.";
  },
];

async function saveChanges() {
  if (dataObject.value === undefined) return;
  if (isValid.value === false) return;

  createApiInstance(DataObjectApi)
    .updateDataObject({
      collectionId: props.collectionId,
      dataObjectId: props.dataObjectId,
      dataObject: dataObject.value,
    })
    .then(_ => {
      // Todo: success message missing
    })
    .catch(error => {
      handleError(error, "updateDataObject");
    });

  showDialog.value = false;
  // Todo: we have to trigger reload in tree view
}
</script>

<template>
  <v-dialog v-model="showDialog" persistent max-width="600">
    <v-card
      :loading="!dataObject"
      title="Edit DataObject"
      :text="props.dataObjectId"
    >
      <template #title>
        <div class="text-h4">Edit "{{ dataObject?.name }}"</div>
      </template>
      <template #text>
        <v-form v-if="!!dataObject" v-model="isValid">
          <v-container class="pa-0">
            <v-row>
              <v-col class="pb-0">
                <div class="text-subtitle-2">Properties</div>
              </v-col>
            </v-row>
            <v-row>
              <v-col>
                <v-text-field
                  v-model="dataObject.name"
                  :rules="nameRules"
                  label="Name*"
                  variant="outlined"
                  density="compact"
                  require
                />
              </v-col>
            </v-row>
            <v-row>
              <v-col class="py-0">
                <div class="text-body-1 text-textbody2">Description</div>
              </v-col>
            </v-row>
            <v-row>
              <v-col>
                <!-- Todo: This should be a generic Texteditor component -->
                <LabJournalEditor
                  v-model="dataObject.description"
                  :initial-content="dataObject.description"
                  is-editable
                />
                <div class="text-body-3 text-textbody2">*mandatory field</div>
              </v-col>
            </v-row>
            <!-- Todo: Relationships are missing -->
          </v-container>
        </v-form>
      </template>
      <template #actions>
        <v-container>
          <v-row justify="end">
            <v-spacer />
            <v-col cols="auto">
              <v-btn variant="flat" @click="showDialog = false">Cancel</v-btn>
              <v-btn
                :disabled="!isValid"
                color="primary"
                variant="flat"
                class="ml-4"
                @click="saveChanges"
              >
                Save Changes
              </v-btn>
            </v-col>
          </v-row>
        </v-container>
      </template>
    </v-card>
  </v-dialog>
</template>
