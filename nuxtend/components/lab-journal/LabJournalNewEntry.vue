<script setup lang="ts">
import {
  LabJournalEntryApi,
  type CreateLabJournalRequest,
  type LabJournalEntry,
} from "@dlr-shepard/backend-client";

const dataObjectId = defineModel<number>({
  required: true,
});

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
    createApiInstance(LabJournalEntryApi)
      .createLabJournal({
        labJournalEntry: newLabJournalEntryModel.value,
        dataObjectId: dataObjectId.value,
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
    variant="outlined"
    label="New entry"
    bg-color="blue-grey-25"
    base-color="black-100"
    :no-resize="true"
    density="compact"
    rows="1"
    @update:focused="isCreatingNew = true"
  />
  <div v-if="!!isCreatingNew" id="labJournalNewEntry">
    <LabJournalEditor
      v-model="newLabJournalEntryModel.journalContent"
      :is-editable="true"
    />

    <div id="newEntryControlButtons">
      <v-btn variant="flat" color="black-50" @click="resetNewLabJournalEntry">
        Cancel
      </v-btn>
      <v-btn variant="flat" color="primary" @click="saveNewLabJournalEntry">
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
