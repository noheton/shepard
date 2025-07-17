<script lang="ts" setup>
import {
  CollectionApi,
  LabJournalEntryApi,
  type LabJournalEntry,
  type Roles,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import LabJournalExistingEntry from "~/components/context/lab-journal/LabJournalExistingEntry.vue";

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

  const promiseList = dataObjectIds.map(dataObjectId =>
    useShepardApi(LabJournalEntryApi)
      .value.getLabJournalsByCollection({ dataObjectId })
      .catch(error => {
        handleError(error, "getLabJournals");
        return null;
      }),
  );

  try {
    const results = await Promise.all(promiseList);
    entries.value = results
      .filter(response => response !== null)
      .flat()
      .sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime());
  } catch (error) {
    handleError(error, "getLabJournals");
  }
}

async function fetchRoles() {
  useShepardApi(CollectionApi)
    .value.getCollectionRoles({ collectionId: props.collectionId })
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

fetchLabJournalEntries(Array.from(props.dataObjectMap.keys()));
fetchRoles();
</script>

<template>
  <div v-if="entries != undefined">
    <LabJournalExistingEntry
      v-for="(entry, index) in entries"
      :key="'lab-journal-' + entry.id"
      :collection-id="collectionId"
      :data-object-id="entry.dataObjectId"
      :data-object-name="props.dataObjectMap.get(entry.dataObjectId)!"
      :lab-journal="entry"
      :user-roles="userRoles"
      @deleted="onLabJournalDeleted(index)"
    />
    <EmptyListIcon
      v-if="entries.length === 0 || entries === undefined"
      label="No entry yet"
    />
  </div>
  <CenteredLoadingSpinner v-else />
</template>
