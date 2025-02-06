<script setup lang="ts">
import {
  CollectionApi,
  LabJournalEntryApi,
  type LabJournalEntry,
  type Roles,
} from "@dlr-shepard/backend-client";

interface CollectionLabJournalEntryListProps {
  collectionId: number;
  dataObjectMap: Map<number, string>;
}

const props = defineProps<CollectionLabJournalEntryListProps>();
const emit = defineEmits(["numberOfEntriesChanged"]);
const entries = ref<LabJournalEntry[] | undefined>(undefined);
const userRoles = ref<Roles | undefined>(undefined);

async function fetchLabJournalEntries(dataObjectIds: number[]) {
  if (dataObjectIds.length === 0) {
    entries.value = [];
  }
  dataObjectIds.forEach(dataObjectId => {
    createApiInstance(LabJournalEntryApi)
      .getLabJournalsByCollection({ dataObjectId })
      .then(response => {
        if (entries.value === undefined) {
          entries.value = [];
        }
        entries.value = entries.value.concat(response);
        emit("numberOfEntriesChanged", entries.value?.length);
      })
      .catch(error => {
        handleError(error, "getLabJournalsByCollection");
      });
  });
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

async function onLabJournalDeleted(deletedLabjournalIndex: number) {
  if (entries.value) {
    entries.value.splice(deletedLabjournalIndex, 1);
    emit("numberOfEntriesChanged", entries.value.length);
  }
}

const sortedLabJournalEntries = computed(() => {
  if (entries.value) {
    const labJournalEntries = entries.value;
    return labJournalEntries.sort((a, b) => a.dataObjectId - b.dataObjectId);
  }
  return undefined;
});

fetchLabJournalEntries(Array.from(props.dataObjectMap.keys()));
fetchRoles();
</script>

<template>
  <div v-if="entries != undefined">
    <LabJournalEntry
      v-for="(entry, index) in sortedLabJournalEntries"
      :key="'lab-journal-' + entry.id"
      :lab-journal="entry"
      :user-roles="userRoles"
      :data-object-link="{
        dataObjectId: entry.dataObjectId,
        dataObjectName: props.dataObjectMap.get(entry.dataObjectId)!,
      }"
      @deleted="onLabJournalDeleted(index)"
    />
    <EmptyListIcon
      v-if="entries.length === 0 || entries === undefined"
      label="No entry yet"
    />
  </div>
  <CenteredLoadingSpinner v-else />
</template>
