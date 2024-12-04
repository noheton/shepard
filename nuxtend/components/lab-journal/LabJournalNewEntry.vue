<script setup lang="ts">
import {
  LabJournalApi,
  type CreateLabJournalRequest,
  type LabJournal,
} from "@dlr-shepard/backend-client";

const dataObjectId = defineModel<number>({
  required: true,
});

const emit = defineEmits<{
  newLabJournalSaved: [value: LabJournal];
}>();

const newLabJournalEntryModel = ref<CreateLabJournalRequest["labJournal"]>({
  dataObjectId: dataObjectId.value,
  journalContent: "",
});
const isCreatingNew = ref<boolean>(false);

async function resetNewLabJournalEntry() {
  newLabJournalEntryModel.value.journalContent = "";
  isCreatingNew.value = false;
}

async function saveNewLabJournalEntry() {
  if (newLabJournalEntryModel.value) {
    createApiInstance(LabJournalApi)
      .createLabJournal({ labJournal: newLabJournalEntryModel.value })
      .then(response => {
        // Todo: add success notification
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
    bg-color="black-grey-25"
    base-color="black-100"
    :no-resize="true"
    density="compact"
    rows="1"
    @mousedown:control="isCreatingNew = true"
  />
  <div v-if="!!isCreatingNew" id="labJournalNewEntry">
    <CommonTextEditor
      v-model="newLabJournalEntryModel.journalContent"
      :is-editable="true"
      :placeholder-content="'Enter your lab journal entry here...'"
    />

    <div id="newEntryControlButtons">
      <v-btn variant="flat" color="black-50" @click="resetNewLabJournalEntry">
        Cancel
      </v-btn>
      <v-btn color="primary" @click="saveNewLabJournalEntry">Save</v-btn>
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
  padding-top: 1em;
  padding-bottom: 1em;
}
</style>
