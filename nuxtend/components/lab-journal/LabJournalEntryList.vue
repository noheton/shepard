<script setup lang="ts">
import {
  CollectionApi,
  LabJournalEntryApi,
  type LabJournalEntry,
  type Roles,
} from "@dlr-shepard/backend-client";

interface LabJournalListProps {
  collectionId: number;
  dataObjectId: number | undefined;
}

const props = defineProps<LabJournalListProps>();
const emit = defineEmits(["numberOfEntriesChanged"]);
const entries = ref<LabJournalEntry[] | undefined>(undefined);
const userRoles = ref<Roles | undefined>(undefined);

async function fetchLabJournalEntries(dataObjectId: number | undefined) {
  if (dataObjectId) {
    createApiInstance(LabJournalEntryApi)
      .getLabJournalsByCollection({ dataObjectId })
      .then(response => {
        entries.value = response;
        emit("numberOfEntriesChanged", entries.value.length);
      })
      .catch(error => {
        handleError(error, "getLabJournalsByCollection");
      });
  }
}

async function fetchRoles() {
  createApiInstance(CollectionApi)
    .getCollectionRoles({ collectionId: props.collectionId })
    .then(response => {
      userRoles.value = response;
    })
    .catch(error => {
      handleError(error, "getCollectionRoles");
    });
}

async function appendNewLabJournalEntry(newLabJournalEntry: LabJournalEntry) {
  if (entries.value) {
    entries.value.unshift(newLabJournalEntry);
    emit("numberOfEntriesChanged", entries.value.length);
  }
}

async function onLabJournalDeleted(deletedLabjournalIndex: number) {
  if (entries.value) {
    entries.value.splice(deletedLabjournalIndex, 1);
    emit("numberOfEntriesChanged", entries.value.length);
  }
}

function isAllowedToCreate() {
  return userRoles.value?.owner || userRoles.value?.writer;
}

fetchLabJournalEntries(props.dataObjectId);
fetchRoles();
</script>

<template>
  <LabJournalNewEntry
    v-if="!!dataObjectId && isAllowedToCreate()"
    :model-value="dataObjectId"
    @new-lab-journal-saved="
      savedLabjournal => appendNewLabJournalEntry(savedLabjournal)
    "
  />
  <div v-if="!!entries">
    <LabJournalEntry
      v-for="(entry, index) in entries"
      :key="'lab-journal-' + entry.id"
      :lab-journal="entry"
      :user-roles="userRoles"
      @deleted="onLabJournalDeleted(index)"
    />
    <CommonEmptyListIcon v-if="entries.length === 0" label="No entry yet" />
  </div>
  <LayoutComponentsCenteredLoadingSpinner v-else />
</template>
