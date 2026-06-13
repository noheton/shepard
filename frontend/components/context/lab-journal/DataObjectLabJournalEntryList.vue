<script lang="ts" setup>
import {
  CollectionApi,
  LabJournalEntryApi,
  type LabJournalEntry,
  type Roles,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

interface DataObjectLabJournalEntryListProps {
  collectionId: number;
  dataObjectId: number;
}

const props = defineProps<DataObjectLabJournalEntryListProps>();
const emit = defineEmits(["numberOfEntriesChanged"]);
const entries = ref<LabJournalEntry[] | undefined>(undefined);
const userRoles = ref<Roles | undefined>(undefined);

async function fetchLabJournalEntries(dataObjectId: number | undefined) {
  // v1 lab-journal needs the numeric id; appId-only DataObjects (post-reset)
  // resolve to undefined. Fail soft to an empty panel instead of an infinite
  // spinner. Proper appId-keyed v2 wiring tracked as UI-DO-LABJOURNAL-V2.
  if (!dataObjectId) {
    entries.value = [];
    return;
  }
  useShepardApi(LabJournalEntryApi)
    .value.getLabJournalsByCollection({ dataObjectId })
    .then(response => {
      entries.value = response;
      emit("numberOfEntriesChanged", entries.value.length);
    })
    .catch(error => {
      entries.value = [];
      handleError(error, "getLabJournalsByCollection");
    });
}

async function fetchRoles() {
  // getCollectionRoles is a v1 numeric-id fallback; skip when the numeric id is
  // unavailable so the page does not surface a spurious null-collectionId toast.
  if (!props.collectionId) {
    return;
  }
  useShepardApi(CollectionApi)
    .value.getCollectionRoles({ collectionId: props.collectionId })
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
    :collection-id="collectionId"
    :data-object-id="dataObjectId"
    @new-lab-journal-saved="
      savedLabjournal => appendNewLabJournalEntry(savedLabjournal)
    "
  />
  <div v-if="!!entries">
    <LabJournalExistingEntry
      v-for="(entry, index) in entries"
      :key="entry.id"
      :collection-id="collectionId"
      :data-object-id="dataObjectId"
      :lab-journal="entry"
      :user-roles="userRoles"
      @deleted="onLabJournalDeleted(index)"
    />
    <EmptyListIcon v-if="entries.length === 0" label="No entry yet" />
  </div>
  <CenteredLoadingSpinner v-else />
</template>
