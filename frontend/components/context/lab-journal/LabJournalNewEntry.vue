<script lang="ts" setup>
import {
  LabJournalEntryApi,
  type CreateLabJournalRequest,
  type LabJournalEntry,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

const props = defineProps<{
  collectionId: number;
  dataObjectId: number;
}>();

const emit = defineEmits<{
  newLabJournalSaved: [value: LabJournalEntry];
}>();

const newLabJournalEntryModel = ref<CreateLabJournalRequest["labJournalEntry"]>(
  {
    journalContent: "",
  },
);
const isCreatingNew = ref<boolean>(false);

async function resetNewLabJournalEntry() {
  newLabJournalEntryModel.value.journalContent = "";
  isCreatingNew.value = false;
}

async function saveNewLabJournalEntry() {
  if (newLabJournalEntryModel.value) {
    useShepardApi(LabJournalEntryApi)
      .value.createLabJournal({
        labJournalEntry: newLabJournalEntryModel.value,
        dataObjectId: props.dataObjectId,
      })
      .then(response => {
        resetNewLabJournalEntry();
        emit("newLabJournalSaved", response);
      })
      .catch(error => {
        handleError(error, "createLabJournal");
      });
  }
}
</script>

<template>
  <v-textarea
    v-if="!isCreatingNew"
    id="newLabJournalEntryArea"
    :no-resize="true"
    base-color="divider1"
    bg-color="divider2"
    density="compact"
    label="New entry"
    rows="1"
    variant="outlined"
    @update:focused="isCreatingNew = true"
  />
  <div v-if="!!isCreatingNew" id="labJournalNewEntry">
    <LabJournalEntry
      v-model:journal-content="newLabJournalEntryModel.journalContent"
      :collection-id="collectionId"
      :data-object-id="dataObjectId"
      :is-editing="true"
    />

    <div id="newEntryControlButtons">
      <v-btn color="treeview" variant="flat" @click="resetNewLabJournalEntry">
        Cancel
      </v-btn>
      <v-btn color="primary" variant="flat" @click="saveNewLabJournalEntry">
        Save
      </v-btn>
    </div>
  </div>
</template>

<style scoped>
#labJournalNewEntry {
  width: 100%;
  display: flex;
  flex-direction: column;
  padding-bottom: 2em;
}

#newEntryControlButtons {
  display: flex;
  justify-content: right;
  gap: 1em;
  padding-right: 1rem;
  padding-top: 1em;
  padding-bottom: 1em;
}
</style>
